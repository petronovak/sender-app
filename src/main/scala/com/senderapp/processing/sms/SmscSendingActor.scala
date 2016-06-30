package com.senderapp.processing.sms

import java.net.URLEncoder

import akka.actor.{Actor, ActorLogging}
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.HostConnectionPool
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.senderapp.Global
import com.senderapp.model.{Events, Message}
import com.senderapp.utils.Utils
import com.typesafe.config.{Config, ConfigFactory}

import scala.collection.JavaConversions._
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}
import Utils._

/**
 * Implementation of a clint for a smsc.ua.
 * See: http://smsc.ua/api/http/
 */
class SmscSendingActor extends Actor with ActorLogging {
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
          log.info(s"Smsc responded with $resp")
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
    val login = config.getString("login")
    val password = config.getString("password")

    val phones = URLEncoder.encode(msg.meta.getString("destination", config.getString("destination")), "UTF-8")
    val from = URLEncoder.encode(msg.meta.getString("fromName", config.getString("fromName")), "UTF-8")
    val body = URLEncoder.encode(msg.body.getOrElse(config.getString("body")), "UTF-8")

    // http://smsc.ua/sys/send.php?login=<login>&psw=<password>&phones=<phones>&mes=<message>
    val url = s"$path?login$login&psw=$password&phone=$phones&sender=$from&mes=$body"
    HttpRequest(uri = url)
  }

  case class SmsResult(response: Try[HttpResponse], msg: Message) extends Serializable
}
