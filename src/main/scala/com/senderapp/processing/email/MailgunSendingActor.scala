package com.senderapp.processing.email

import akka.actor.{ Actor, ActorLogging }
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.HostConnectionPool
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.model.headers.{ Authorization, BasicHttpCredentials }
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse, _ }
import akka.stream.scaladsl.{ Flow, Sink, Source }
import com.senderapp.Global
import com.senderapp.model.{ Events, Message }
import com.typesafe.config.{ Config, ConfigFactory }

import scala.collection.immutable.Seq
import scala.concurrent.duration._
import scala.util.{ Failure, Success, Try }

class MailgunSendingActor extends Actor with ActorLogging {
  import Global._

  var connectionPoolFlowOpt: Option[Flow[(HttpRequest, Message), (Try[HttpResponse], Message), HostConnectionPool]] = None

  var config: Config = _

  val timeout = 1000.millis

  var headersConf: List[Map[String, String]] = Nil

  override def receive: Receive = {
    case jsMsg: Message =>

      log.info(s"$jsMsg")

      val url = s"/v3/${config.getString("domain")}/messages"
      val entity = buildRequest(jsMsg)
      val authHeaders = Seq(Authorization(BasicHttpCredentials("api", config.getString("key"))))

      sendRequest(
        RequestBuilding.Post(url).withHeadersAndEntity(authHeaders, entity) -> jsMsg
      )

    case result: MailgunResult =>
      result.response match {
        case Success(resp) =>
          log.info(s"Mailgun responded with $resp")
          val future = resp.entity.toStrict(timeout).map { _.data.utf8String }
          future.onComplete { d =>
            log.info(s"Data: ${d.get}")
          }
        case Failure(ex) =>
          log.warning("Error sending request to mandrill", ex)
      }

    case Events.Configure(name, newConfig) =>
      configure(newConfig)
    case unknown =>
      log.error("Received unknown data: " + unknown)
  }

  def configure(newConfig: Config) {
    config = newConfig.withFallback(ConfigFactory.defaultReference().getConfig("mailgun"))

    implicit val system = context.system

    // do not restart connection pool it doesn't change anyway
    if (connectionPoolFlowOpt.isEmpty) {
      connectionPoolFlowOpt = Some(Http().cachedHostConnectionPoolTls[Message](config.getString("host")))
    }
  }

  def sendRequest(request: (HttpRequest, Message)) =
    Source.single(request).via(connectionPoolFlowOpt.get).runWith(Sink.foreach {
      case (response, msg) =>
        self ! MailgunResult(response, msg)
    })

  def buildRequest(msg: Message): RequestEntity = {
    val fromEmail = msg.meta.get("fromEmail").map(_.toString).getOrElse(config.getString("fromEmail"))
    val destination = msg.meta.get("destination").map(_.toString).getOrElse(config.getString("destination"))
    val headers = headersConf ++ msg.meta.get("headers").map(_.asInstanceOf[List[Map[String, String]]]).getOrElse(List())
    val subject = msg.meta.get("subject").map(_.toString).getOrElse(config.getString("subject"))

    /* val headersJs = headers.map { m =>
      val item = m.head
      ("h:" + item._1) -> item._2
    } */

    FormData(
      "from" -> fromEmail,
      "to" -> destination,
      "subject" -> subject,
      "html" -> msg.body.getOrElse("")
    ).toEntity
  }

  case class MailgunResult(response: Try[HttpResponse], msg: Message) extends Serializable
}

