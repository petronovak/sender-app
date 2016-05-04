package com.senderapp.processing.http

import akka.actor.{ Actor, ActorLogging }
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.model.HttpHeader.ParsingResult
import akka.http.scaladsl.model._
import akka.pattern.pipe
import akka.util.ByteString
import com.senderapp.Global
import com.senderapp.model.{ Events, Message }
import com.senderapp.utils.Utils
import com.typesafe.config.{ Config, ConfigFactory }
import scala.collection.JavaConversions._
import scala.concurrent.duration._

class HttpSendingActor extends Actor with ActorLogging {
  import Global._

  var config: Config = _

  val timeout = 1000.millis

  val http = Http(context.system)
  var headersConf: List[Map[String, String]] = _

  override def receive: Receive = {
    case msg: Message =>
      log.info(s"$msg")
      sendRequest(msg)
    case result: HttpResult =>
      val resp = result.response
      log.info(s"Server responded with ${resp.status} : ${result.data}")
    case Events.Configure(name, newConf) =>
      config = newConf.withFallback(ConfigFactory.defaultReference().getConfig("http"))
      headersConf = config.getObjectList("headers").map(Utils.unwrap).toList.asInstanceOf[List[Map[String, String]]]
    case unknown =>
      log.error("Received unknown data: " + unknown)
  }

  def sendRequest(msg: Message) = {
    val urlstr = msg.meta.getOrElse("destination", config.getString("destination")).toString
    val method = msg.meta.getOrElse("method", config.getString("method")).toString.toLowerCase

    val headers = headersConf ++ msg.meta.get("headers").map { _.asInstanceOf[List[Map[String, String]]] }.getOrElse(List())
    val akkaHeaders = parseHeaders(headers)

    log.info(s"Calling $method $urlstr, headers: $headers")

    val request = method match {
      case "get" =>
        RequestBuilding.Get(uri = urlstr)
      case "post" =>
        val body = ByteString(msg.body.getOrElse(""))
        RequestBuilding.Post(uri = urlstr).withEntity(HttpEntity(contentType = getContentType(akkaHeaders), data = body))
      case "put" =>
        val body = ByteString(msg.body.getOrElse(""))
        RequestBuilding.Put(uri = urlstr).withEntity(HttpEntity(contentType = getContentType(akkaHeaders), data = body))
      case "delete" =>
        RequestBuilding.Delete(uri = urlstr)
    }

    val requestWithHeaders = request.addHeaders(akkaHeaders)

    http.singleRequest(request = requestWithHeaders).flatMap { r =>
      r.entity.toStrict(timeout).map(entity => HttpResult(r, entity.data.utf8String, msg))
    }.pipeTo(self)
  }

  private def parseHeaders(headers: List[Map[String, String]]): List[HttpHeader] = {
    headers.map { m =>
      val head = m.head
      HttpHeader.parse(head._1, head._2) match {
        case ok: ParsingResult.Ok =>
          ok.header
        case err: ParsingResult.Error =>
          throw new RuntimeException(s"Invalid header: $head")
      }
    }
  }

  private def getContentType(akkaHeaders: List[HttpHeader]) =
    akkaHeaders.find(_.is("content-type")) flatMap { t =>

      MediaType.parse(t.value()) match {
        case Right(mediaType) =>
          Some(ContentType.apply(mediaType, () => HttpCharsets.`UTF-8`))
        case Left(header) =>
          log.warning(s"Wrong content type header: $header")
          None
      }

    } getOrElse ContentTypes.`application/json`

  case class HttpResult(response: HttpResponse, data: String, msg: Message)
}

