package com.senderapp.processing.http

import akka.Done
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.model.HttpHeader.ParsingResult
import akka.http.scaladsl.model._
import akka.util.ByteString
import com.senderapp.Global
import com.senderapp.model.Message
import com.senderapp.processing.AbstractSendingActor
import com.senderapp.processing.AbstractSendingActor.SendResult
import com.senderapp.utils.Utils
import com.senderapp.utils.Utils._
import com.typesafe.config.{ Config, ConfigFactory }

import scala.collection.JavaConversions._
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try

class HttpSendingActor extends AbstractSendingActor {

  import Global._

  override val provider: String = "http"
  override val timeout = 1000.millis

  val http = Http(context.system)

  def buildRequest(msg: Message): HttpRequest = {
    val urlstr = msg.meta.getStringOpt("destination").getOrElse(config.getString("destination"))
    val method = msg.meta.getStringOpt("method").getOrElse(config.getString("method")).toLowerCase

    val headers = headersConf // TODO: ++ msg.meta.get("headers").map { _.asInstanceOf[List[Map[String, String]]] }.getOrElse(List())
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

    request.addHeaders(akkaHeaders)
  }

  override def configure(newConfig: Config) {
    config = newConfig.withFallback(ConfigFactory.defaultReference().getConfig(provider))
    headersConf = Try(config.getObjectList("headers")).toOption.map(hs => hs.map(Utils.unwrap).toList.asInstanceOf[List[Map[String, String]]]).getOrElse(Nil)
    log.info(s"Configure $provider sending actor")
  }

  override def sendRequest(request: (HttpRequest, Message)) = {
    http.singleRequest(request._1).onComplete {
      case resp => self ! SendResult(resp, request._2)
    }
    Future.successful(Done)
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

}
