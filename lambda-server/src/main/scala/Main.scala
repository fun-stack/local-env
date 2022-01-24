package funstack.lambdaserver

import scala.scalajs.js
import cats.implicits._

import typings.node.fsMod
import typings.node.pathMod

import scala.scalajs.js.timers

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

  def parse(
    modeString: String,
    jsFileName: String,
    exportName: String,
    portString: Option[String],
  ): Either[String, Config] =
    for {
      port <- portString.traverse(str => str.toIntOption.toRight(s"Unexpected port number: $str"))
      mode <- Mode.fromString(modeString).toRight(s"Undefined mode, expected 'ws|http', got: $modeString")
    } yield Config(mode, jsFileName, exportName, port)
}

object Main {
  def main(_args: Array[String]): Unit = {
    // TODO process facade? or how to get args correctly?
    val args =
      js.Dynamic.global.process.argv.asInstanceOf[js.Array[String]].toList.tail.tail // ignore node and filename arg

    parse(args) match {
      case Right(config) =>
        var cancel = () => ()

        var watcher: Option[fsMod.FSWatcher]             = None
        var lastTimeout: Option[timers.SetTimeoutHandle] = None

        def run(): Unit = {
          cancel()
          cancel = start(config) match {
            case Right(newCancel) =>
              println("Server started")
              newCancel
            case Left(error)      =>
              println(s"Error starting server: $error")
              () => ()
          }
        }

        def watch(): Unit = {
          lastTimeout = None
          val w = fsMod.watch(
            config.jsFileName,
            { (_, _) =>
              println("File changed, restarting...")
              run()
            },
          )

          watcher = Some(w)

          w.on(
            "error",
            { _ =>
              watcher.foreach(_.close())
              watcher = None
              lastTimeout.foreach(timers.clearTimeout)
              lastTimeout = Some(timers.setTimeout(500)(watch()))
            },
          )
        }

        run()
        watch()

      case Left(error) =>
        println(s"Error parsing arguments: $error")
    }
  }

  def parse(args: List[String]): Either[String, Config] = args match {
    case List(modeString, jsFileName, exportName, portString) =>
      Config.parse(modeString, jsFileName, exportName, Some(portString))
    case List(modeString, jsFileName, exportName)             => Config.parse(modeString, jsFileName, exportName, None)
    case other                                                =>
      Left(s"Invalid arguments, expected '<http|ws> <js-file-name> <export> [<port>]', got: ${other.mkString(", ")}")
  }

  def requireUncached(module: String): js.Dynamic = {
    import js.Dynamic.{global => g}
    val requireCache = g.__non_webpack_require__.cache.asInstanceOf[js.Dictionary[js.Any]]
    val moduleKey    = g.__non_webpack_require__.resolve(module).asInstanceOf[String]
    requireCache.remove(moduleKey)
    g.__non_webpack_require__(module)
  }

  def start(config: Config): Either[String, () => Unit] =
    for {
      requiredJs      <-
        Either
          .catchNonFatal(requireUncached(pathMod.resolve(config.jsFileName)))
          .left
          .map(e => s"Cannot require js file: $e")
      exportedHandler <-
        requiredJs
          .selectDynamic(config.exportName)
          .asInstanceOf[js.UndefOr[js.Any]]
          .toRight(s"Cannot access export '${config.exportName}'")

      cancel = config.mode match {
                 case Mode.Http =>
                   val function = exportedHandler.asInstanceOf[http.DevServer.FunctionType]
                   val server   = http.DevServer.start(function, port = config.port.getOrElse(8080))
                   () => server.close()
                 case Mode.Ws   =>
                   val function = exportedHandler.asInstanceOf[ws.DevServer.FunctionType]
                   val server   = ws.DevServer.start(function, port = config.port.getOrElse(8081))
                   () => server.close()
               }
    } yield { () =>
      val _ = cancel()
    }
}
