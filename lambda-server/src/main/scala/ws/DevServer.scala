package funstack.lambdaserver.ws

import net.exoego.facade.aws_lambda.APIGatewayProxyStructuredResultV2
import net.exoego.facade.aws_lambda
import typings.ws.mod.WebSocketServer
import typings.ws.mod.ServerOptions
import typings.ws.wsStrings
import typings.jwtDecode.mod.{default => jwt_decode}
import typings.jwtDecode.mod.JwtPayload
import scala.scalajs.js
import scala.scalajs.js.annotation.JSExport
import scala.util.{Failure, Success}
import cats.implicits._

private[lambdaserver] object WebsocketConnections {
  import scala.collection.mutable

  private val connections   = mutable.HashMap[String, String => Unit]()              // connectionId -> send-method
  private val subscriptions = mutable.HashMap[String, mutable.ArrayBuffer[String]]() // subscriptionKey -> [connectionId]

  def connect(connectionId: String, send: String => Unit): Unit =
    connections(connectionId) = send

  def disconnect(connectionId: String): Unit =
    connections -= connectionId

  def subscribe(connectionId: String, subscriptionKey: String): Unit = {
    val buf = subscriptions.getOrElseUpdate(subscriptionKey, new mutable.ArrayBuffer())
    buf += connectionId
  }

  def unsubscribe(connectionId: String, subscriptionKey: String): Unit =
    subscriptions.get(subscriptionKey).foreach { buf =>
      buf -= connectionId
    }

  def send(subscriptionKey: String, body: String): Unit = subscriptions.get(subscriptionKey).foreach { buf =>
    buf.foreach(connectionId => connections.get(connectionId).foreach(_(body)))
  }
}

object DevServer {
  type FunctionType = js.Function2[APIGatewayWSEvent, aws_lambda.Context, js.Promise[APIGatewayProxyStructuredResultV2]]

  import scala.concurrent.ExecutionContext.Implicits.global

  def start(lambdaHandler: FunctionType, port: Int): WebSocketServer = {
    val wss = new WebSocketServer(ServerOptions().setPort(port.toDouble))
    wss.on_connection(
      wsStrings.connection,
      { (_, ws, msg) =>
        println("WS> new connection")

        val token = {
          val queryParam = msg.url.get.split("=") // TODO
          if (queryParam.size > 1) queryParam(1) else ""
        }

        val connectionId = util.Random.alphanumeric.take(20).mkString
        WebsocketConnections.connect(connectionId, ws.send(_))

        ws.on_close(
          wsStrings.close,
          (ws, code, reason) => WebsocketConnections.disconnect(connectionId),
        )

        ws.on_message(
          wsStrings.message,
          { (_, data, _) =>
            val body = data.toString
            // check if internal message
            val json = Either.catchNonFatal(js.JSON.parse(body)).toOption
            json.flatMap(_.__action.asInstanceOf[js.UndefOr[String]].toOption) match {
              // subscription handling
              case Some("subscribe")   => WebsocketConnections.subscribe(connectionId, json.get.subscription_key.asInstanceOf[String])
              case Some("unsubscribe") => WebsocketConnections.unsubscribe(connectionId, json.get.subscription_key.asInstanceOf[String])
              case _                   =>
                // call lambda
                val (event, context) = transform(body, token, connectionId)
                lambdaHandler(event, context).toFuture.onComplete {
                  case Success(result) =>
                    ws.send(result.body)
                  case Failure(error)  =>
                    print("WS> ")
                    error.printStackTrace()
                }
            }
          },
        )
      },
    )

    wss
  }

  def transform(body: String, accessToken: String, connectionId: String): (APIGatewayWSEvent, aws_lambda.Context) = {

    val authorizer =
      if (accessToken == "")
        js.Dynamic.literal(
          principalId = "anon",
        )
      else {
        js.Object.assign(
          js.Dynamic
            .literal(
              principalId = "user",
            )
            .asInstanceOf[js.Object],
          jwt_decode[JwtPayload](accessToken),
        )
      }

    val randomRequestId = util.Random.alphanumeric.take(20).mkString
    val randomMessageId = util.Random.alphanumeric.take(20).mkString
    val now             = new js.Date()

    val event = js.Dynamic
      .literal(
        requestContext = js.Dynamic.literal(
          routeKey = "$default",
          authorizer = authorizer,
          messageId = randomMessageId,
          eventType = "MESSAGE",
          extendedRequestId = randomRequestId,
          requestTime = now.toISOString(), // TODO: ISO 8601 maybe not correct. Examples have "21/Nov/2020:20:39:08 +0000" which is a different format,
          messageDirection = "IN",
          stage = "latest",
          connectedAt = now.getUTCMilliseconds(),
          requestTimeEpoch = now.getUTCMilliseconds(),
          identity = js.Dynamic.literal(
            userAgent = "Mozilla/5.0 (X11; Linux x86_64; rv =94.0) Gecko/20100101 Firefox/94.0",
            sourceIp = "127.0.0.1",
          ),
          requestId = randomRequestId,
          domainName = "localhost",
          connectionId = connectionId,
          apiId = "jck5a4ero8",
        ),
        body = body,
        isBase64Encoded = false,
      )
      .asInstanceOf[APIGatewayWSEvent]

    val lambdaContext = js.Dynamic
      .literal(
        callbackWaitsForEmptyEventLoop = true,
        functionVersion = "$LATEST",
        functionName = "function-name",
        memoryLimitInMB = "256",
        logGroupName = "/aws/lambda/function-name",
        logStreamName = "2021/11/16/[$LATEST]fd71a67c7c5a4c68ad4d591119ebfdae",
        invokedFunctionArn = "arn:aws:lambda:eu-central-1:123456789012:function:function-name",
        awsRequestId = "3ddc97fc-c7f9-45fc-856b-c3efdad9c544",
      )
      .asInstanceOf[aws_lambda.Context]

    (event, lambdaContext)
  }

}
