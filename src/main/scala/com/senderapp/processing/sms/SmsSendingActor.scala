package com.senderapp.processing.sms

import akka.actor.{ Actor, ActorLogging }
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.HostConnectionPool
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse }
import akka.stream.scaladsl.{ Flow, Sink, Source }
import com.senderapp.Global
import com.senderapp.model.{ Events, Message }
import com.senderapp.utils.Utils
import com.typesafe.config.{ Config, ConfigFactory }
import spray.json._

import scala.collection.JavaConversions._
import scala.concurrent.duration._
import scala.util.{ Failure, Success, Try }

class SmsSendingActor extends Actor with ActorLogging {
  import Global._

  var connectionPoolFlowOpt: Option[Flow[(HttpRequest, Message), (Try[HttpResponse], Message), HostConnectionPool]] = None

  var config: Config = _

  val timeout = 1000.millis

  var headersConf: List[Map[String, String]] = _

  override def receive: Receive = {
    case jsMsg: Message =>
      log.info(s"$jsMsg")
      sendRequest(buildRequest(jsMsg) -> jsMsg)
    case result: SmsResult =>
      result.response match {
        case Success(resp) =>
          log.info(s"Unisend responded with $resp")
          val future = resp.entity.toStrict(timeout).map { _.data.utf8String }
          future.onComplete { d =>
            log.info(s"Data: ${d.get}")
          }
        case Failure(ex) =>
          log.warning("Error sending request to unisend", ex)
      }

    case Events.Configure(name, newConfig) =>
      configure(newConfig)
    case unknown =>
      log.error("Received unknown data: " + unknown)
  }

  def configure(newConfig: Config) {
    config = newConfig.withFallback(ConfigFactory.defaultReference().getConfig("mandrill"))
    headersConf = config.getObjectList("headers").map(Utils.unwrap).toList.asInstanceOf[List[Map[String, String]]]
    implicit val system = context.system

    // do not restart connection pool it doesn't change anyway
    if (connectionPoolFlowOpt.isEmpty) {
      connectionPoolFlowOpt = Some(Http().cachedHostConnectionPoolTls[Message](config.getString("host"), config.getInt("port")))
    }
  }

  def sendRequest(request: (HttpRequest, Message)) =
    Source.single(request).via(connectionPoolFlowOpt.get).runWith(Sink.foreach {
      case (response, msg) =>
        self ! SmsResult(response, msg)
    })

  def buildRequest(msg: Message): HttpRequest = {
    val path = config.getString("path")
    val key = config.getString("key")
    val phones = msg.meta.get("destination").map(_.toString).getOrElse(config.getString("destination"))
    val from = msg.meta.get("fromName").map(_.toString).getOrElse(config.getString("fromName"))
    val body = msg.body.map(JsString(_)).getOrElse(config.getString("body"))

    val url = s"$path?format=json&api_key=$key&phone=$phones&sender=$from&text=$body"
    HttpRequest(uri = url)
  }

  case class SmsResult(response: Try[HttpResponse], msg: Message) extends Serializable
}
