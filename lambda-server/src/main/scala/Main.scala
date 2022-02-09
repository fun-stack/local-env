package funstack.lambdaserver

import scala.scalajs.js
import cats.implicits._
import typings.node.fsMod
import typings.node.pathMod

import scala.scalajs.js.timers
import typings.node.processMod.global.process

sealed trait Mode
object Mode {
  case object HTTP extends Mode
  case object WS   extends Mode

  def fromString(str: String): Option[Mode] = Some(str.toLowerCase).collect {
    case "http" => HTTP
    case "ws"   => WS
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
  def setupGlobalDevEnvironment(): Unit = {
    import funstack.lambdaserver.ws

    js.Dynamic.global.global.fun_dev_environment = js.Dynamic.literal(send_subscription = ws.WebsocketConnections.send: js.Function2[String, String, Unit])
  }

  def main(@annotation.unused _args: Array[String]): Unit = {
    setupGlobalDevEnvironment()

    val args = process.argv.toList

    parseArgs(args) match {
      case Right(configs) => configs.foreach(run)

      case Left(error) => println(s"Error parsing arguments: $error")
    }
  }

  def run(config: Config): Unit = {
    var cancel = () => ()

    var watcher: Option[fsMod.FSWatcher]             = None
    var lastTimeout: Option[timers.SetTimeoutHandle] = None

    def run(): Unit = {
      cancel()
      cancel = start(config) match {
        case Right(newCancel) =>
          println(s"${config.mode}> Server started")
          newCancel
        case Left(error)      =>
          println(s"${config.mode}> Error starting server: $error")
          retry()
          () => ()
      }
    }

    def watch(): Unit = {
      lastTimeout.foreach(timers.clearTimeout)
      lastTimeout = None

      watcher.foreach(_.close())
      watcher = None

      try {
      val w: fsMod.FSWatcher = fsMod.watch(
        filename = config.jsFileName,
        listener = { (_, _) =>
          println(s"${config.mode}> File changed, restarting...")
          run()
          watch() // since the file might have been deleted, reinitialize the watcher
        },
      )

      watcher = Some(w)
        run() // initial run

      w.on(
        "error",
          err => retry(s"${config.mode}> File watching error: $err"),
      )
    }
      catch {
        error =>
          error match {
            case error if error.getMessage().startsWith("Error: ENOENT: no such file or directory") =>
              retry("File not found.")
            case _                                                                                  =>
              error.printStackTrace()
          }
      }
    }

    def retry(msg: String = ""): Unit = {
      println(s"${if (msg.nonEmpty) s"$msg " else ""}Retrying...")
      lastTimeout.foreach(timers.clearTimeout)
      lastTimeout = Some(timers.setTimeout(2000)(watch()))
    }

    watch()
  }

  def parseArgs(args: List[String]): Either[String, List[Config]] =
    args
      .drop(2) // ignore node and filename arg
      .foldLeft[List[List[String]]](List(Nil)) { (acc, cur) =>
        if (cur == "--") Nil :: acc else (cur :: acc.head) :: acc.tail
      }
      .reverse
      .map(_.reverse)
      .traverse(parse)

  def parse(args: List[String]): Either[String, Config] = args match {
    case List(modeString, jsFileName, exportName, portString) =>
      Config.parse(modeString, jsFileName, exportName, Some(portString))
    case List(modeString, jsFileName, exportName)             =>
      Config.parse(modeString, jsFileName, exportName, None)
    case other                                                =>
      Left(s"Invalid arguments, expected '<http|ws> <js-file-name> <export> [<port>] [-- ...]', got: ${other.mkString(", ")}")
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
                 case Mode.HTTP =>
                   val function = exportedHandler.asInstanceOf[http.DevServer.FunctionType]
                   val server   = http.DevServer.start(function, port = config.port.getOrElse(8080))
                   () => server.close()
                 case Mode.WS   =>
                   val function = exportedHandler.asInstanceOf[ws.DevServer.FunctionType]
                   val server   = ws.DevServer.start(function, port = config.port.getOrElse(8081))
                   () => server.close()
               }
    } yield { () =>
      val _ = cancel()
    }
}
