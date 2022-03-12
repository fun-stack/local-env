package funstack.local

sealed trait ParseResult[+T]
object ParseResult {
  case object Help                  extends ParseResult[Nothing]
  case class Error(message: String) extends ParseResult[Nothing]
  case class Success[T](value: T)   extends ParseResult[T]

  def fromEither[T]: Either[String, T] => ParseResult[T] = {
    case Right(value) => Success(value)
    case Left(error)  => Error(error)
  }

  def foldCombine[T]: (ParseResult[List[T]], ParseResult[T]) => ParseResult[List[T]] = {
    case (ParseResult.Success(values), ParseResult.Success(value))  => ParseResult.Success(value :: values)
    case (ParseResult.Error(message1), ParseResult.Error(message2)) => ParseResult.Error(s"$message2, $message1")
    case (ParseResult.Help, _)                                      => ParseResult.Help
    case (_, ParseResult.Help)                                      => ParseResult.Help
    case (error: ParseResult.Error, _)                              => error
    case (_, error: ParseResult.Error)                              => error
  }
}

sealed trait Config
object Config {
  case class Http(port: Option[Int]) extends Config
  case class Ws(port: Option[Int])   extends Config
  case class Auth(port: Option[Int]) extends Config

  sealed trait Handler extends Config {
    def mode: String
    def jsFileName: String
    def exportName: String
  }

  case class HttpApi(jsFileName: String, exportName: String)           extends Handler { def mode = "HttpApi"           }
  case class HttpRpc(jsFileName: String, exportName: String)           extends Handler { def mode = "HttpRpc"           }
  case class WsRpc(jsFileName: String, exportName: String)             extends Handler { def mode = "WsRpc"             }
  case class WsEventAuthorizer(jsFileName: String, exportName: String) extends Handler { def mode = "WsEventAuthorizer" }

  val helpMessage = """Usage: fun-stack-local <options>
         |--http [<port>]
         |--ws [<port>]
         |--auth [<port>]
         |--http-api <js-file-name> <export-name>
         |--http-rpc <js-file-name> <export-name>
         |--ws-rpc <js-file-name> <export-name>
         |--ws-event-authorizer <js-file-name> <export-name>""".stripMargin

  def parse(args: List[String]): ParseResult[Config] =
    args match {
      case ("-h" :: _) | ("--help" :: _) => ParseResult.Help

      case "--http" :: args =>
        args match {
          case Nil              => ParseResult.Success(Http(None))
          case List(portString) => ParseResult.fromEither(portString.toIntOption.map(port => Http(Some(port))).toRight(s"Unexpected port number: $portString"))
          case _                => ParseResult.Error("Error parsing arguments. Expected: --http [<port>]")
        }

      case "--ws" :: args   =>
        args match {
          case Nil              => ParseResult.Success(Ws(None))
          case List(portString) => ParseResult.fromEither(portString.toIntOption.map(port => Ws(Some(port))).toRight(s"Unexpected port number: $portString"))
          case _                => ParseResult.Error("Error parsing arguments. Expected: --ws [<port>]")
        }
      case "--auth" :: args =>
        args match {
          case Nil              => ParseResult.Success(Auth(None))
          case List(portString) => ParseResult.fromEither(portString.toIntOption.map(port => Auth(Some(port))).toRight(s"Unexpected port number: $portString"))
          case _                => ParseResult.Error("Error parsing arguments. Expected: --auth [<port>]")
        }

      case "--http-api" :: args =>
        args match {
          case List(jsFileName, exportName) => ParseResult.Success(HttpApi(jsFileName, exportName))
          case _                            => ParseResult.Error("Error parsing arguments. Expected: --http-api <js-file-name> <export-name>")
        }

      case "--http-rpc" :: args =>
        args match {
          case List(jsFileName, exportName) => ParseResult.Success(HttpRpc(jsFileName, exportName))
          case _                            => ParseResult.Error("Error parsing arguments. Expected: --http-rpc <js-file-name> <export-name>")
        }

      case "--ws-rpc" :: args =>
        args match {
          case List(jsFileName, exportName) => ParseResult.Success(WsRpc(jsFileName, exportName))
          case _                            => ParseResult.Error("Error parsing arguments. Expected: --ws-rpc <js-file-name> <export-name>")
        }

      case "--ws-event-authorizer" :: args =>
        args match {
          case List(jsFileName, exportName) => ParseResult.Success(WsEventAuthorizer(jsFileName, exportName))
          case _                            => ParseResult.Error("Error parsing arguments. Expected: --ws-event-authorizer <js-file-name> <export-name>")
        }

      case args => ParseResult.Error(s"Error parsing arguments. Got: $args")
    }

  def parseArgs(args: List[String]): ParseResult[List[Config]] =
    args
      .foldLeft[List[List[String]]](Nil) {
        case (acc, cur) if cur.startsWith("-") => List(cur) :: acc
        case (prev :: acc, cur)                => (cur :: prev) :: acc
        case (acc, cur)                        => List(cur) :: acc
      }
      .map(_.reverse)
      .map(parse)
      .foldLeft[ParseResult[List[Config]]](ParseResult.Success(Nil))(ParseResult.foldCombine)
}
