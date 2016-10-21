package com.senderapp.processing

import akka.Done
import akka.actor.{ Actor, ActorLogging }
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.HostConnectionPool
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse }
import akka.stream.scaladsl.{ Flow, Sink, Source }
import com.senderapp.Global
import com.senderapp.model.{ Events, Message }
import com.senderapp.processing.AbstractSendingActor.SendResult
import com.senderapp.utils.Utils
import com.typesafe.config.{ Config, ConfigFactory }

import scala.collection.JavaConversions._
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{ Failure, Success, Try }

/**
 *
 * @author Yaroslav Derman <yaroslav.derman@gmail.com>
 *         created on 04/10/16
 */
abstract class AbstractSendingActor extends Actor with ActorLogging {
  import Global._

  val provider: String

  var connectionPoolFlowOpt: Option[Flow[(HttpRequest, Message), (Try[HttpResponse], Message), HostConnectionPool]] = None
  var config: Config = _
  val timeout = 5 seconds
  var headersConf: List[Map[String, String]] = Nil

  def buildRequest(msg: Message): HttpRequest

  override def receive: Receive = {
    case jsMsg: Message =>
      log.info(s"Receive msg $jsMsg for $provider")
      sendRequest(buildRequest(jsMsg) -> jsMsg)
    case result: SendResult =>
      result.response match {
        case Success(resp) =>
          log.info(s"$provider responded with $resp")
          val future = resp.entity.toStrict(timeout).map { _.data.utf8String }
          future.onComplete { d =>
            log.info(s"Data response from $provider: ${d.get}")
          }
        case Failure(ex) =>
          log.warning(s"Error sending request to $provider: {}", ex)
      }

    case Events.Configure(name, newConfig) =>
      configure(newConfig)
    case unknown =>
      log.error(s"Received unknown data for provider $provider: " + unknown)
  }

  def sendRequest(request: (HttpRequest, Message)): Future[Done] =
    Source.single(request).via(connectionPoolFlowOpt.get).runWith(Sink.foreach {
      case (response, msg) =>
        self ! SendResult(response, msg)
    })

  def configure(newConfig: Config) {
    config = newConfig.withFallback(ConfigFactory.defaultReference().getConfig(provider))
    headersConf = Try(config.getObjectList("headers")).toOption.map(hs => hs.map(Utils.unwrap).toList.asInstanceOf[List[Map[String, String]]]).getOrElse(Nil)
    implicit val system = context.system

    // do not restart connection pool it doesn't change anyway
    if (connectionPoolFlowOpt.isEmpty) {
      connectionPoolFlowOpt = config.getInt("port") match {
        case 443 => Some(Http().cachedHostConnectionPoolHttps[Message](config.getString("host")))
        case 80  => Some(Http().cachedHostConnectionPool[Message](config.getString("host")))
      }
    }

    log.info(s"Configure $provider sending actor")
  }

}

object AbstractSendingActor {
  case class SendResult(response: Try[HttpResponse], msg: Message) extends Serializable
}
