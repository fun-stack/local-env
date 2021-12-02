package funstack.lambdaserver

import scala.scalajs.js
import cats.implicits._

import typings.node.fsMod
import typings.node.pathMod

sealed trait Mode
object Mode {
  case object Http extends Mode
  case object Ws   extends Mode

  def fromString(str: String): Option[Mode] = Some(str.toLowerCase).collect {
    case "http" => Http
    case "ws"   => Ws
  }
}

case class Config(mode: Mode, jsFileName: String, exportName: String, port: Option[Int])
object Config {

  def parse(modeString: String, jsFileName: String, exportName: String, portString: Option[String]): Either[String, Config] =
    for {
      port <- portString.traverse(str => str.toIntOption.toRight(s"Unexpected port number: $str"))
      mode <- Mode.fromString(modeString).toRight(s"Undefined mode, expected 'ws|http', got: $modeString")
    } yield Config(mode, jsFileName, exportName, port)
}

object Main {
  def main(_args: Array[String]): Unit = {
    //TODO process facade? or how to get args correctly?
    val args = js.Dynamic.global.process.argv.asInstanceOf[js.Array[String]].toList.tail.tail // ignore node and filename arg

    parse(args) match {
      case Right(config) =>
        println(s"Config: $config")

        var cancel = () => ()

        def run(): Unit = {
          cancel()
          cancel = start(config) match {
            case Right(newCancel) =>
              println("Server started")
              newCancel
            case Left(error) =>
              println(s"Error starting server: $error")
              () => ()
          }
        }

        run()

        val _ = fsMod.watch(config.jsFileName, { (_,_) =>
          println("File changed, restarting...")
          run()
        })

      case Left(error)   =>
        println(s"Error parsing arguments: $error")
    }
  }

  def parse(args: List[String]): Either[String, Config] = args match {
    case List(modeString, jsFileName, exportName, portString) => Config.parse(modeString, jsFileName, exportName, Some(portString))
    case List(modeString, jsFileName, exportName)             => Config.parse(modeString, jsFileName, exportName, None)
    case other                                         => Left(s"Invalid arguments, expected '<http|ws> <js-file-name> <export> [<port>]', got: ${other.mkString(", ")}")
  }

  def start(config: Config): Either[String, () => Unit] = {
    for {
      requiredJs      <- Either.catchNonFatal(js.Dynamic.global.__non_webpack_require__(pathMod.resolve(config.jsFileName))).left.map(e => s"Cannot require js file: $e")
      exportedHandler <- Either.catchNonFatal(requiredJs.selectDynamic(config.exportName)).left.map(e => s"Cannot access export: $e ${js.JSON.stringify(requiredJs)}")
      cancel          <- config.mode match {
        case Mode.Http =>
          Either.catchNonFatal(exportedHandler.asInstanceOf[http.DevServer.FunctionType])
            .left.map(e => s"Exported Http handler is of unexpected type: $e")
            .map(http.DevServer.start(_, port = config.port.getOrElse(8080)))
            .map(server => () => server.close())
        case Mode.Ws   =>
          Either.catchNonFatal(exportedHandler.asInstanceOf[ws.DevServer.FunctionType])
            .left.map(e => s"Exported Ws handler is of unexpected type: $e")
            .map(ws.DevServer.start(_, port = config.port.getOrElse(8081)))
            .map(server => () => server.close())
      }
    } yield { () => val _ = cancel() }
  }
}
