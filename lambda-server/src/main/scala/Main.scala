package funstack.lambdaserver

import scala.scalajs.js
import cats.implicits._
import typings.node.fsMod
import typings.node.pathMod

import scala.scalajs.js.timers
import typings.node.processMod.global.process

sealed trait Config {
  def mode: String
  def jsFileName: String
  def exportName: String
}
object Config       {
  case class HTTP(jsFileName: String, exportName: String, port: Option[Int]) extends Config { def mode = "HTTP"      }
  case class WS(jsFileName: String, exportName: String, port: Option[Int])   extends Config { def mode = "WS"        }
  case class EventAuth(jsFileName: String, exportName: String)               extends Config { def mode = "EventAuth" }

  def parse(mode: String, args: List[String]): Either[String, Config] =
    mode.toLowerCase match {
      case "http"      =>
        args match {
          case List(jsFileName, exportName)             => Right(HTTP(jsFileName, exportName, None))
          case List(jsFileName, exportName, portString) =>
            portString.toIntOption.map { port =>
              HTTP(jsFileName, exportName, Some(port))
            }.toRight(s"Unexpected port number: $portString")
          case _                                        => Left("Expected: http <js-file-name> <export-name> [<port>]")
        }

      case "ws"        =>
        args match {
          case List(jsFileName, exportName)             => Right(WS(jsFileName, exportName, None))
          case List(jsFileName, exportName, portString) =>
            portString.toIntOption.map { port =>
              WS(jsFileName, exportName, Some(port))
            }.toRight(s"Unexpected port number: $portString")
          case _                                        => Left("Expected: ws <js-file-name> <export-name> [<port>]")
        }

      case "eventauth" =>
        args match {
          case List(jsFileName, exportName) => Right(EventAuth(jsFileName, exportName))
          case _                            => Left("Expected: eventauth <js-file-name> <export-name>")
        }

      case mode        => Left(s"Expected mode <http|ws|eventauth>, got: $mode")
    }
}

object Main {

  def setupGlobalDevEnvironment(): Unit =
    js.Dynamic.global.global.fun_dev_environment = js.Dynamic.literal(
      send_subscription = ws.WebsocketConnections.sendSubscription: js.Function2[String, String, Unit],
      send_connection = ws.WebsocketConnections.sendConnection: js.Function2[String, String, Unit],
    )

  def main(@annotation.unused _args: Array[String]): Unit = {
    setupGlobalDevEnvironment()

    val args = process.argv.toList

    parseArgs(args) match {
      case Right(configs) => configs.foreach(run)
      case Left(error)    => println(s"Error parsing arguments: $error")
    }
  }

  def run(config: Config): Unit = {
    var watcher: Option[fsMod.FSWatcher]             = None
    var lastTimeout: Option[timers.SetTimeoutHandle] = None

    println(s"${config.mode}> Starting")
    initialize(config)

    def run(): Unit =
      start(config) match {
        case Right(())   => ()
        case Left(error) =>
          println(s"${config.mode}> Error: $error")
          retry()
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
            println(s"${config.mode}> File changed, resetting...")
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
        case error: Throwable if error.getMessage().startsWith("Error: ENOENT: no such file or directory") =>
          retry("File not found.")
        case error: Throwable                                                                              =>
          error.printStackTrace()
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
    case modeString :: tail => Config.parse(modeString, tail)
    case _                  => Left("Got no arguments, expected mode.")
  }

  def requireUncached(module: String): js.Dynamic = {
    import js.Dynamic.{global => g}
    val requireCache = g.__non_webpack_require__.cache.asInstanceOf[js.Dictionary[js.Any]]
    val moduleKey    = g.__non_webpack_require__.resolve(module).asInstanceOf[String]
    requireCache.remove(moduleKey)
    g.__non_webpack_require__(module)
  }

  def initialize(config: Config): Unit =
    config match {
      case config: Config.HTTP =>
        http.DevServer.start(port = config.port.getOrElse(8080))
        ()
      case config: Config.WS   =>
        ws.DevServer.start(port = config.port.getOrElse(8081))
        ()
      case _: Config.EventAuth =>
        ()
    }

  def start(config: Config): Either[String, Unit] =
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
    } yield config match {
      case _: Config.HTTP      =>
                   val function = exportedHandler.asInstanceOf[http.DevServer.FunctionType]
                   http.DevServer.lambdaHandler = Some(function)
      case _: Config.WS        =>
                   val function = exportedHandler.asInstanceOf[ws.DevServer.FunctionType]
                   ws.DevServer.lambdaHandler = Some(function)
                 case _: Config.EventAuth =>
                   val function = exportedHandler.asInstanceOf[ws.WebsocketConnections.AuthFunctionType]
                   ws.WebsocketConnections.eventAuthorizer = Some(function)
               }
}
