package funstack.lambdaserver

import cats.implicits._

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

  def parse(mode: String, args: List[String]): Either[String, Config] =
    mode match {
      case "-h" | "--help" =>
        List()
        Left("""Usage: lambda-server <options>
               |--http [<port>]
               |--ws [<port>]
               |--auth [<port>]
               |--http-api <js-file-name> <export-name>
               |--http-rpc <js-file-name> <export-name>
               |--ws-rpc <js-file-name> <export-name>
               |--ws-event-authorizer <js-file-name> <export-name>""".stripMargin)

      case "--http" =>
        args match {
          case Nil              => Right(Http(None))
          case List(portString) => portString.toIntOption.map(port => Http(Some(port))).toRight(s"Unexpected port number: $portString")
          case _                => Left("Error parsing arguments. Expected: --http [<port>]")
        }

      case "--ws"   =>
        args match {
          case Nil              => Right(Ws(None))
          case List(portString) => portString.toIntOption.map(port => Ws(Some(port))).toRight(s"Unexpected port number: $portString")
          case _                => Left("Error parsing arguments. Expected: --ws [<port>]")
        }
      case "--auth" =>
        args match {
          case Nil              => Right(Auth(None))
          case List(portString) => portString.toIntOption.map(port => Auth(Some(port))).toRight(s"Unexpected port number: $portString")
          case _                => Left("Error parsing arguments. Expected: --auth [<port>]")
        }

      case "--http-api" =>
        args match {
          case List(jsFileName, exportName) => Right(HttpApi(jsFileName, exportName))
          case _                            => Left("Error parsing arguments. Expected: --http-api <js-file-name> <export-name>")
        }

      case "--http-rpc" =>
        args match {
          case List(jsFileName, exportName) => Right(HttpRpc(jsFileName, exportName))
          case _                            => Left("Error parsing arguments. Expected: --http-rpc <js-file-name> <export-name>")
        }

      case "--ws-rpc" =>
        args match {
          case List(jsFileName, exportName) => Right(WsRpc(jsFileName, exportName))
          case _                            => Left("Error parsing arguments. Expected: --ws-rpc <js-file-name> <export-name>")
        }

      case "--ws-event-authorizer" =>
        args match {
          case List(jsFileName, exportName) => Right(WsEventAuthorizer(jsFileName, exportName))
          case _                            => Left("Error parsing arguments. Expected: --ws-event-authorizer <js-file-name> <export-name>")
        }

      case mode => Left(s"Error parsing arguments. Expected mode --<help|http|ws|http-api|http-rpc|ws-rpc|ws-event-authorizer>, got: $mode")
    }

  def parseArgs(args: List[String]): Either[String, List[Config]] =
    args
      .foldLeft[List[List[String]]](Nil) {
        case (acc, cur) if cur.startsWith("-") => List(cur) :: acc
        case (prev :: acc, cur)                => (cur :: prev) :: acc
        case (acc, cur)                        => List(cur) :: acc
      }
      .reverse
      .map(_.reverse)
      .traverse(parseArgsGroup)

  def parseArgsGroup(args: List[String]): Either[String, Config] = args match {
    case modeString :: tail => parse(modeString, tail)
    case args               => Left(s"Error parsing argument: $args")
  }
}
