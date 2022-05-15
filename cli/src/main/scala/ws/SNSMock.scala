package funstack.local.ws

import net.exoego.facade.aws_lambda._
import net.exoego.facade.aws_lambda
import scala.scalajs.js

object SNSMock {
  def transform(body: String, userId: Option[String], connectionId: String): (SNSEvent, aws_lambda.Context) = {
    val now = new js.Date

    val messageId = java.util.UUID.randomUUID()

    val event = SNSEvent(
      Records = js.Array(
        SNSEventRecord(
          EventSource = "aws:sns",
          EventVersion = "1.0",
          EventSubscriptionArn = "arn:aws:sns:eu-central-1:123456789012:function-name:791d3138-73d2-4ba4-8d48-46e012a1ea1d",
          Sns = SNSMessage(
            SignatureVersion = "1",
            Signature = "TODO",
            Timestamp = now.toISOString(),
            MessageId = messageId.toString,
            Message = body,
            MessageAttributes = js.Object
              .assign(
                js.Dynamic.literal(
                  connection_id = SNSMessageAttribute(Type = "String", Value = connectionId),
                ),
                userId.fold(js.Dynamic.literal()) { userId =>
                  js.Dynamic.literal(user_id = SNSMessageAttribute(Type = "String", Value = userId))
                },
              )
              .asInstanceOf[js.Dictionary[SNSMessageAttribute]],
            Type = "Notification",
            SigningCertUrl = "https://example.com",
            UnsubscribeUrl = "https://example.com",
            TopicArn = "arn:aws:sns:eu-central-1:123456789012:topic-name",
            Subject = null,
          ),
        ),
      ),
    )

    val context = js.Dynamic
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

    (event, context)
  }
}
