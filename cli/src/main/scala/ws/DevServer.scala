package funstack.local.ws

import cats.effect.IO
import cats.effect.std.Semaphore
import cats.effect.unsafe.implicits.{global => unsafeIORuntimeGlobal}
import cats.implicits._
import funstack.local.helper.{AccessToken, Base64Codec}
import net.exoego.facade.aws_lambda
import net.exoego.facade.aws_lambda.APIGatewayProxyStructuredResultV2
import typings.ws.mod.{ServerOptions, WebSocketServer}
import typings.ws.wsStrings

import scala.scalajs.js
import org.scalajs.dom.console

private[local] object WebsocketConnections {
  import scala.collection.mutable

  type AuthFunctionType = js.Function2[aws_lambda.SNSEvent, aws_lambda.Context, js.Promise[Unit]]

  var eventAuthorizer: Option[AuthFunctionType] = None

  private val connections   = mutable.HashMap[String, String => Unit]()              // connectionId -> send-method
  private val users         = mutable.HashMap[String, String]()                      // connectionId -> userId
  private val subscriptions = mutable.HashMap[String, mutable.ArrayBuffer[String]]() // subscriptionKey -> [connectionId]

  def connect(connectionId: String, userId: Option[String], send: String => Unit): Unit = {
    connections(connectionId) = send
    userId.foreach(users(connectionId) = _)
  }

  def disconnect(connectionId: String): Unit = {
    connections -= connectionId
    users -= connectionId
    subscriptions.keys.foreach(unsubscribe(connectionId, _))
  }

  def subscribe(connectionId: String, subscriptionKey: String): Unit = {
    val buf = subscriptions.getOrElseUpdate(subscriptionKey, new mutable.ArrayBuffer)
    buf += connectionId
  }

  def unsubscribe(connectionId: String, subscriptionKey: String): Unit =
    subscriptions.get(subscriptionKey).foreach { buf =>
      buf -= connectionId
    }

  def sendSubscription(subscriptionKey: String, body: String): Unit = {
    console.log("subsciptionKey: " + subscriptionKey + ", subscriptionMap: " + subscriptions)
    subscriptions
      .get(subscriptionKey)
      .foreach { connections =>
        console.log("  connections: " + connections)
        connections.foreach { connectionId =>
          eventAuthorizer match {
            case None             =>
              sendConnection(connectionId, body)
            case Some(authorizer) =>
              val userId           = users.get(connectionId)
              val (event, context) = SNSMock.transform(body, userId = userId, connectionId = connectionId)
              println(s"WsEventAuthorizer> authorize event for user '$userId'")
              authorizer(event, context)
          }
        }
      }
  }

  def sendConnection(connectionId: String, body: String): Unit = connections.get(connectionId).foreach(_(body))
}

object DevServer {
  type FunctionType = js.Function2[APIGatewayWSEvent, aws_lambda.Context, js.Promise[APIGatewayProxyStructuredResultV2]]

  var lambdaHandler: Option[FunctionType] = None

  val semaphore = Semaphore[IO](1).unsafeToFuture()

  import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits._

  def start(port: Int): WebSocketServer = {
    val wss = new WebSocketServer(ServerOptions().setPort(port.toDouble))
    wss.on_connection(
      wsStrings.connection,
      { (_, ws, msg) =>
        println("Ws> new connection")

        val accessToken = {
          val queryParam = msg.url.get.split("=") // TODO
          if (queryParam.size > 1) Some(queryParam(1)) else None
        }

        val authorizer = AccessToken.toAuthorizer(accessToken)

        val userId = authorizer.sub.asInstanceOf[js.UndefOr[String]].toOption

        val connectionId = util.Random.alphanumeric.take(20).mkString
        WebsocketConnections.connect(connectionId, userId, ws.send(_))

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
                val (event, context) = transform(body, authorizer, connectionId)
                lambdaHandler.foreach { handler =>
                  for {
                    semaphore <- semaphore
                    _         <- semaphore.acquire.unsafeToFuture()
                    result    <- handler(event, context).toFuture.attempt
                    _         <- semaphore.release.unsafeToFuture()
                  } yield result match {
                    case Right(result) =>
                      val body = result.body.map { body =>
                        if (result.isBase64Encoded.getOrElse(false)) Base64Codec.decode(body) else body
                      }

                      ws.send(body)
                    case Left(error)   =>
                      print("Ws> ")
                      error.printStackTrace()
                  }
                }
            }
          },
        )
      },
    )

    wss
  }

  def transform(body: String, authorizer: js.Dynamic, connectionId: String): (APIGatewayWSEvent, aws_lambda.Context) = {

    val randomRequestId = util.Random.alphanumeric.take(20).mkString
    val randomMessageId = util.Random.alphanumeric.take(20).mkString
    val now             = new js.Date

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
