package funstack.local.http

import cats.effect.IO
import cats.effect.std.Semaphore
import cats.effect.unsafe.implicits.{global => unsafeIORuntimeGlobal}
import cats.implicits._
import funstack.local.helper.{AccessToken, Base64Codec}
import net.exoego.facade.aws_lambda
import net.exoego.facade.aws_lambda.{APIGatewayProxyEventV2, APIGatewayProxyStructuredResultV2}
import typings.node.httpMod.{createServer, IncomingMessage, Server, ServerResponse}
import typings.node.{Buffer => JsBuffer}

import java.net.URI
import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.js.JSConverters._

object DevServer {
  type FunctionType =
    js.Function2[APIGatewayProxyEventV2, aws_lambda.Context, js.Promise[APIGatewayProxyStructuredResultV2]]

  var lambdaHandler: Option[FunctionType]           = None
  var lambdaHandlerUnderscore: Option[FunctionType] = None

  val semaphore = Semaphore[IO](1).unsafeToFuture()

  import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits._

  def start(port: Int): Server = {
    val requestListener = { (req: IncomingMessage, res: ServerResponse) =>
      val body = new StringBuilder

      res.setHeader("Access-Control-Allow-Origin", "*");
      res.setHeader("Access-Control-Request-Method", "*");
      res.setHeader("Access-Control-Allow-Methods", "OPTIONS, GET, PUT, POST, DELETE, HEAD, TRACE, CONNECT");
      res.setHeader("Access-Control-Allow-Headers", "*");

      req.on(
        "data",
        chunk => {
          val buffer = chunk.asInstanceOf[JsBuffer];
          body ++= buffer.toString()
          ()
        },
      )
      req.on(
        "end",
        _ =>
          if (req.method.exists(_ == "OPTIONS")) { // catch preflight requests
            res.end()
          }
          else {
            val bodyStr                       = body.result()
            val (gatewayEvent, lambdaContext) = transform(s"http://localhost:$port", req, bodyStr)

            val handler = req.url.toOption match {
              case Some(url) if url.startsWith("/_/") => lambdaHandlerUnderscore
              case _                                  => lambdaHandler
            }

            handler match {
              case Some(handler) =>
                for {
                  semaphore <- semaphore
                  _         <- semaphore.acquire.unsafeToFuture()
                  result    <- handler(gatewayEvent, lambdaContext).toFuture.attempt
                  _         <- semaphore.release.unsafeToFuture()
                } yield result match {
                  case Right(result) =>
                    result.headers.foreach { headers =>
                      headers.foreach { case (key, value) =>
                        res.setHeader(key, value.toString)
                      }
                    }
                    result.statusCode.foreach(res.statusCode = _)

                    val body = result.body.map { body =>
                      if (result.isBase64Encoded.getOrElse(false)) Base64Codec.decode(body) else body
                    }

                    res.end(body)
                  case Left(error)   =>
                    res.statusCode = 500 // internal server error
                    print("Http> ")
                    error.printStackTrace()
                    res.end()
                }
              case None          =>
                res.statusCode = 404 // not found
                res.end()
            }

            ()
          },
      )

      ()
    }

    val server = createServer(requestListener)
    server.listen(port.toDouble)
    server
  }

  def transform(baseUrl: String, req: IncomingMessage, body: String): (APIGatewayProxyEventV2, aws_lambda.Context) = {

    val url             = new URI(s"$baseUrl${req.url.getOrElse("")}")
    val queryParameters = Option(url.getQuery)
      .fold(Map.empty[String, String])(
        _.split("&|=")
          .grouped(2)
          .map(a => a(0) -> a(1))
          .toMap,
      )

    // https://docs.aws.amazon.com/lambda/latest/dg/services-apigateway.html#apigateway-example-event
    // example json: https://github.com/awsdocs/aws-lambda-developer-guide/blob/main/sample-apps/nodejs-apig/event-v2.json
    // more examples: https://github.com/search?l=JSON&q=pathparameters+requestContext+isbase64encoded&type=Code
    val routeKey = "ANY /nodejs-apig-function-1G3XMPLZXVXYI"
    val now      = new js.Date
    val host     = Option(url.getHost()).getOrElse("") // TODO: includes port number, but it shouldn't?
    val path     = s"/latest${url.getPath()}"          // TODO: why latest?

    val authHeader  = req.headers.asInstanceOf[js.Dictionary[String]].get("authorization")
    val accessToken = authHeader.map(_.split(" ")).collect { case Array("Bearer", token) => token }

    val authorizerDict = AccessToken.toAuthorizer(accessToken)

    val randomRequestId = util.Random.alphanumeric.take(20).mkString
    val gateWayEvent    = APIGatewayProxyEventV2(
      version = "2.0",
      routeKey = routeKey,
      rawPath = path,
      rawQueryString = Option(url.getQuery()).getOrElse(""),
      cookies = req.headers.cookie.fold(js.Array[String]())(_.split(";").toJSArray),
      // TODO: multi valued headers
      // TODO: include cookies in headers?
      headers = req.headers.asInstanceOf[js.Dictionary[String]],
      requestContext = APIGatewayProxyEventV2.RequestContext(
        accountId = "123456789012",
        apiId = "r3pmxmplak",
        authorizer = js.Dictionary("lambda" -> authorizerDict),
        domainName = host,
        domainPrefix = host.split(".").headOption.getOrElse(url.getHost()),
        http = APIGatewayProxyEventV2.RequestContext.Http(
          method = req.method.getOrElse(""),
          path = path,
          protocol = "HTTP/1.1",
          sourceIp = "127.0.0.1",
          userAgent = req.headers.`user-agent`.getOrElse(""),
        ),                        // RequestContext.Http
        requestId = randomRequestId,
        routeKey = routeKey,
        stage = "$default",
        time = now.toISOString(), // TODO: ISO 8601 maybe not correct. Examples have "21/Nov/2020:20:39:08 +0000" which is a different format,
        timeEpoch = now.getUTCMilliseconds(),
      ),
      isBase64Encoded = false,
      body = body,
      pathParameters = js.undefined,                         // TODO: js.Dictionary for /{id}/ in URL
      queryStringParameters = queryParameters.toJSDictionary,// js.Dictionary[String](),
    )

    val lambdaContext = js.Dynamic
      .literal(
        callbackWaitsForEmptyEventLoop = true,
        functionName = "function",
        functionVersion = "$LATEST",
        invokedFunctionArn = "arn:aws:lambda:ap-southeast-2:[AWS_ACCOUNT_ID]:function:restapi",
        memoryLimitInMB = "128",
        awsRequestId = "1d9ccf1c-0f09-427e-b2f8-ffc961d25904",
        logGroupName = "",
        logStreamName = s"/aws/lambda/function",
        // var identity: js.UndefOr[aws_lambda.CognitoIdentity]    = js.undefined
        // var clientContext: js.UndefOr[aws_lambda.ClientContext] = js.undefined
      )
      .asInstanceOf[aws_lambda.Context]

    (gateWayEvent, lambdaContext)
  }
}
