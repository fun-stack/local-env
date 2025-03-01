package funstack.local

import cats.implicits._
import typings.node.processMod.global.process
import typings.node.{fsMod, pathMod}

import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import scala.scalajs.js.timers

object Main {

  def setupGlobalDevEnvironment(configs: List[Config]): Unit = {
    val hasWs   = configs.exists {
      case _: Config.Ws => true
      case _            => false
    }
    val authUrl = configs.collectFirst { case auth: Config.Auth =>
      s"http://localhost:${auth.portOrDefault}"
    }
    val hasAuth = authUrl.isDefined

    js.Dynamic.global.global.fun_dev_environment = js.Dynamic.literal(
      sendSubscription = Option.when(hasWs)(ws.WebsocketConnections.sendSubscription: js.Function2[String, String, Unit]).orUndefined,
      sendConnection = Option.when(hasWs)(ws.WebsocketConnections.sendConnection: js.Function2[String, String, Unit]).orUndefined,
      getEmail = Option.when(hasAuth)(auth.AuthMock.getEmailForUser: js.Function1[String, String]).orUndefined,
      authUrl = authUrl.orUndefined,
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
    var watcher: Option[fsMod.FSWatcher]                  = None
    var lastTimeoutRun: Option[timers.SetTimeoutHandle]   = None
    var lastTimeoutWatch: Option[timers.SetTimeoutHandle] = None

    val jsFilePath     = pathMod.parse(config.jsFileName)
    val jsFileName     = jsFilePath.base
    val jsParentFolder = jsFilePath.dir

    def run(): Unit = {
      lastTimeoutRun.foreach(timers.clearTimeout)
      lastTimeoutRun = None

      setHandler(config) match {
        case Right(())   => ()
        case Left(error) =>
          println(s"${config.mode}> Error: $error")
          retry()
      }
    }

    def watch(): Unit = {
      lastTimeoutWatch.foreach(timers.clearTimeout)
      lastTimeoutWatch = None

      watcher.foreach(_.close())
      watcher = None

      try {
        val w: fsMod.FSWatcher = fsMod.watch(
          filename = jsParentFolder,
          listener = { (event, filename) =>
            println(s"${config.mode}> File watcher triggered. Event: ${event}, Filename: ${filename}")
            val parsedFilePath = pathMod.parse(filename)
            val parsedFilename = parsedFilePath.base
            if (parsedFilename == jsFileName) {
              println(s"${config.mode}> File changed, resetting...")
              lastTimeoutRun.foreach(timers.clearTimeout)
              lastTimeoutRun = Some(timers.setTimeout(1000) {
                run()
                watch() // since the file might have been deleted, reinitialize the watcher
              })
            }
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
          retry("Compiled js file does not exist yet.")
        case error: Throwable                                                                              =>
          error.printStackTrace()
      }
    }

    def retry(msg: String = ""): Unit = {
      println(s"${if (msg.nonEmpty) s"$msg " else ""}Retrying...")
      lastTimeoutWatch.foreach(timers.clearTimeout)
      lastTimeoutWatch = Some(timers.setTimeout(2000)(watch()))
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
        val port = config.portOrDefault
        println(s"Http> Starting Http server on port $port")
        http.DevServer.start(port = port)
        ()
      case config: Config.Ws   =>
        val port = config.portOrDefault
        println(s"Ws> Starting Ws server on port $port")
        ws.DevServer.start(port = port)
        ()
      case config: Config.Auth =>
        val port = config.portOrDefault
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
          .map(exception => s"Error when requiring js file: $exception")
      exportedHandler <-
        requiredJs
          .selectDynamic(config.exportName)
          .asInstanceOf[js.UndefOr[js.Any]]
          .toRight(s"Cannot access export '${config.exportName}'")
    } yield config match {
      case _: Config.HttpApi           =>
        val function = logWrapper(config.mode, exportedHandler.asInstanceOf[http.DevServer.FunctionType])
        http.DevServer.lambdaHandler = Some(function)
      case _: Config.HttpRpc           =>
        val function = logWrapper(config.mode, exportedHandler.asInstanceOf[http.DevServer.FunctionType])
        http.DevServer.lambdaHandlerUnderscore = Some(function)
      case _: Config.WsRpc             =>
        val function = logWrapper(config.mode, exportedHandler.asInstanceOf[ws.DevServer.FunctionType])
        ws.DevServer.lambdaHandler = Some(function)
      case _: Config.WsEventAuthorizer =>
        val function = logWrapper(config.mode, exportedHandler.asInstanceOf[ws.WebsocketConnections.AuthFunctionType])
        ws.WebsocketConnections.eventAuthorizer = Some(function)
    }

  def logWrapper[A, B, R](mode: String, function: js.Function2[A, B, js.Promise[R]]): js.Function2[A, B, js.Promise[R]] = { (a, b) =>
    Either.catchNonFatal(function(a, b)) match {
      case Right(r) =>
        r.`catch`[R]({ (error: Any) =>
          println(s"$mode> Error in function promise: $error")
          js.Promise.reject(error)
        }: js.Function1[Any, js.Thenable[R]])
      case Left(e)  =>
        println(s"$mode> Error in function: $e")
        throw e
    }
  }
}
