package funstack.local

import cats.implicits._
import typings.node.fsMod
import typings.node.pathMod
import typings.node.processMod.global.process

import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import scala.scalajs.js.timers
import java.io.PrintWriter
import java.io.StringWriter

object Main {

  def setupGlobalDevEnvironment(configs: List[Config]): Unit = {
    val hasWs   = configs.exists {
      case _: Config.Ws => true
      case _            => false
    }
    val hasAuth = configs.exists {
      case _: Config.Auth => true
      case _              => false
    }

    js.Dynamic.global.global.fun_dev_environment = js.Dynamic.literal(
      sendSubscription = Option.when(hasWs)(ws.WebsocketConnections.sendSubscription: js.Function2[String, String, Unit]).orUndefined,
      sendConnection = Option.when(hasWs)(ws.WebsocketConnections.sendConnection: js.Function2[String, String, Unit]).orUndefined,
      getEmail = Option.when(hasAuth)(auth.AuthMock.getEmailForUser: js.Function1[String, String]).orUndefined,
    )
  }

  def main(@annotation.unused _args: Array[String]): Unit = {
    val args = process.argv.toList.drop(2) // ignore node and filename args

    Config.parseArgs(args) match {
      case ParseResult.Error(message) =>
        println(s"Error: $message")
        println(Config.helpMessage)

      case ParseResult.Success(Nil) | ParseResult.Help =>
        println(Config.helpMessage)

      case ParseResult.Success(configs) =>
        println(configs)
        setupGlobalDevEnvironment(configs)
        configs.foreach { config =>
          initialize(config)

          config match {
            case config: Config.Handler => watch(config)
            case _                      => ()
          }
        }
    }
  }

  def watch(config: Config.Handler): Unit = {
    var watcher: Option[fsMod.FSWatcher]             = None
    var lastTimeout: Option[timers.SetTimeoutHandle] = None

    def run(): Unit =
      setHandler(config) match {
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

  def requireUncached(module: String): js.Dynamic = {
    import js.Dynamic.{global => g}
    val requireCache = g.__non_webpack_require__.cache.asInstanceOf[js.Dictionary[js.Any]]
    val moduleKey    = g.__non_webpack_require__.resolve(module).asInstanceOf[String]
    requireCache.remove(moduleKey)
    g.__non_webpack_require__(module)
  }

  def initialize(config: Config): Unit =
    config match {
      case config: Config.Http =>
        val port = config.port.getOrElse(8080)
        println(s"Http> Starting Http server on port $port")
        http.DevServer.start(port = port)
        ()
      case config: Config.Ws   =>
        val port = config.port.getOrElse(8081)
        println(s"Ws> Starting Ws server on port $port")
        ws.DevServer.start(port = port)
        ()
      case config: Config.Auth =>
        val port = config.port.getOrElse(8082)
        println(s"Auth> Starting Auth server on port $port")
        auth.DevServer.start(port = port)
        ()
      case _                   =>
        ()
    }

  def setHandler(config: Config.Handler): Either[String, Unit] =
    for {
      requiredJs      <-
        Either
          .catchNonFatal(requireUncached(pathMod.resolve(config.jsFileName)))
          .left
          .map(exception => s"Error when requiring js file: ${exception}")
      exportedHandler <-
        requiredJs
          .selectDynamic(config.exportName)
          .asInstanceOf[js.UndefOr[js.Any]]
          .toRight(s"Cannot access export '${config.exportName}'")
    } yield config match {
      case _: Config.HttpApi           =>
        val function = exportedHandler.asInstanceOf[http.DevServer.FunctionType]
        http.DevServer.lambdaHandler = Some(function)
      case _: Config.HttpRpc           =>
        val function = exportedHandler.asInstanceOf[http.DevServer.FunctionType]
        http.DevServer.lambdaHandlerUnderscore = Some(function)
      case _: Config.WsRpc             =>
        val function = exportedHandler.asInstanceOf[ws.DevServer.FunctionType]
        ws.DevServer.lambdaHandler = Some(function)
      case _: Config.WsEventAuthorizer =>
        val function = exportedHandler.asInstanceOf[ws.WebsocketConnections.AuthFunctionType]
        ws.WebsocketConnections.eventAuthorizer = Some(function)
    }
}
