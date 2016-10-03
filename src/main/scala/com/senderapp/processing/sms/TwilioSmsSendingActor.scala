package com.senderapp.processing.sms

import java.net.URLEncoder

import akka.actor.{Actor, ActorLogging}
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.HostConnectionPool
import akka.http.scaladsl.model.{FormData, HttpMethods, HttpRequest, HttpResponse}
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.senderapp.Global
import com.senderapp.model.{Events, Message}
import com.senderapp.utils.Utils
import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}
import Utils._
import akka.http.scaladsl.model.headers.{Authorization, BasicHttpCredentials}

/**
  * Implementation of a clint for Twilio.com.
  * See: https://www.twilio.com/docs/quickstart/java/sms
  * @author Sergey Khruschak
  */
class TwilioSmsSendingActor extends Actor with ActorLogging {
  import Global._

  var connectionPoolFlowOpt: Option[Flow[(HttpRequest, Message), (Try[HttpResponse], Message), HostConnectionPool]] = None

  var config: Config = _

  val timeout = 5 seconds

  override def receive: Receive = {
    case jsMsg: Message =>
      log.info(s"$jsMsg")
      sendRequest(buildRequest(jsMsg) -> jsMsg)
    case result: SmsResult =>
      result.response match {
        case Success(resp) =>
          log.info(s"Twilio responded with $resp")
          val future = resp.entity.toStrict(timeout).map { _.data.utf8String }
          future.onComplete { d =>
            log.info(s"Data: ${d.get}")
          }
        case Failure(ex) =>
          log.warning("Error sending request to twilio: {}", ex)
      }

    case Events.Configure(name, newConfig) =>
      configure(newConfig)
    case unknown =>
      log.error("Received unknown data: " + unknown)
  }

  def configure(newConfig: Config) {
    config = newConfig.withFallback(ConfigFactory.defaultReference().getConfig("smsc"))
    implicit val system = context.system

    // do not restart connection pool it doesn't change anyway
    if (connectionPoolFlowOpt.isEmpty) {
      connectionPoolFlowOpt = Some(Http().cachedHostConnectionPool[Message](config.getString("host"), port = config.getInt("port")))
    }
  }

  def sendRequest(request: (HttpRequest, Message)) =
    Source.single(request).via(connectionPoolFlowOpt.get).runWith(Sink.foreach {
      case (response, msg) =>
        self ! SmsResult(response, msg)
    })

  def buildRequest(msg: Message): HttpRequest = {

    val baseUrl = config.getString("url")
    val accountSid = config.getString("accountSid")
    val token = config.getString("token")

    val phone = msg.meta.getString("destination", config.getString("destination"))
    val from = msg.meta.getString("fromName", config.getString("fromName"))
    val body = msg.body.getOrElse(config.getString("body"))

    val url = s"$baseUrl/Accounts/$accountSid/Messages"
    log.info(s"Sending twilio request: https://${config.getString("host")}$url")

    HttpRequest(uri = url, method = HttpMethods.POST,
                entity = FormData("From" -> from, "To" -> phone, "Body" -> body).toEntity)
          .withHeaders(Authorization(BasicHttpCredentials(accountSid, token)))
  }

  case class SmsResult(response: Try[HttpResponse], msg: Message) extends Serializable
}
