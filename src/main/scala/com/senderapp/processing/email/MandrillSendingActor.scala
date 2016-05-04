package com.senderapp.processing.email

import akka.actor.{ ActorLogging, Actor }
import akka.http.javadsl.model.HttpEntities
import akka.http.scaladsl.Http.HostConnectionPool
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.{ HttpResponse, HttpRequest }
import akka.http.scaladsl.Http
import akka.stream.scaladsl.{ Flow, Sink, Source }
import com.senderapp.Global
import com.senderapp.model.{ Events, JsonMessageData, Message }
import com.senderapp.utils.Utils
import com.typesafe.config.{ ConfigFactory, Config }
import spray.json._
import scala.collection.JavaConversions._
import scala.util.{ Failure, Success, Try }
import scala.concurrent.duration._

class MandrillSendingActor extends Actor with ActorLogging {
  import Global._

  var connectionPoolFlowOpt: Option[Flow[(HttpRequest, Message), (Try[HttpResponse], Message), HostConnectionPool]] = None

  var config: Config = _

  val timeout = 1000.millis

  var headersConf: List[Map[String, String]] = _

  override def receive: Receive = {
    case jsMsg: Message =>
      log.info(s"$jsMsg")
      val entity = HttpEntities.create(MediaTypes.`application/json`.toContentType, buildRequest(jsMsg))
      sendRequest(RequestBuilding.Post(config.getString("path")).withEntity(entity) -> jsMsg)
    case result: MandrillResult =>
      result.response match {
        case Success(resp) =>
          log.info(s"Mandrill responded with $resp")
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
    config = newConfig.withFallback(ConfigFactory.defaultReference().getConfig("mandrill"))
    headersConf = config.getObjectList("headers").map(Utils.unwrap).toList.asInstanceOf[List[Map[String, String]]]
    implicit val system = context.system
    // implicit val materializer = ActorFlowMaterializer()

    // do not restart connection pool it doesn't change anyway
    if (connectionPoolFlowOpt.isEmpty) {
      connectionPoolFlowOpt = Some(Http().cachedHostConnectionPoolTls[Message](config.getString("host"), config.getInt("port")))
    }
  }

  def sendRequest(request: (HttpRequest, Message)) =
    Source.single(request).via(connectionPoolFlowOpt.get).runWith(Sink.foreach {
      case (response, msg) =>
        self ! MandrillResult(response, msg)
    })

  def buildRequest(msg: Message): String = {
    //val tmplField = msg.meta.get("template").map(_.toString).getOrElse(config.getString("template"))

    val json = JsObject(
      "key" -> JsString(config.getString("key")),
      // "template_name" -> JsString(tmplField),
      // "template_content" -> JsArray(tplData),
      "message" -> toMandrillMessage(msg)
    )

    val jsonStr = json.compactPrint
    log.debug(s"Json body: $jsonStr")
    jsonStr
  }

  def toMandrillMessage(msg: Message): JsObject = {
    val fromEmail = msg.meta.get("fromEmail").map(_.toString).getOrElse(config.getString("fromEmail"))
    val fromName = msg.meta.get("fromName").map(_.toString).getOrElse(config.getString("fromName"))
    val destination = msg.meta.get("destination").map(_.toString).getOrElse(config.getString("destination"))
    val headers = headersConf ++ msg.meta.get("headers").map(_.asInstanceOf[List[Map[String, String]]]).getOrElse(List())
    val subject = msg.meta.get("subject").map(_.toString).getOrElse(config.getString("subject"))

    val headersJs = headers.map { m =>
      val item = m.head
      (item._1, JsString(item._2))
    }

    val tplData = msg.data match {
      case json: JsonMessageData =>
        JsArray(json.js.asJsObject.fields.toSeq.map {
          case (k, v) => JsObject(
            "key" -> JsString(k),
            "content" -> v
          )
        }: _*)
      case _ => JsArray.empty
    }

    JsObject(
      "subject" -> JsString(subject),
      "from_email" -> JsString(fromEmail),
      "from_name" -> JsString(fromName),
      "important" -> JsBoolean(msg.meta.get("important").map(_.toString.toBoolean).getOrElse(false)),
      "to" -> JsArray(
        JsObject("email" -> JsString(destination))
      ),
      "headers" -> JsObject(headersJs: _*),
      "merge_language" -> JsString("handlebars"),
      "global_merge_vars" -> tplData,
      "html" -> msg.body.map(JsString(_)).getOrElse(JsNull)
    )
  }

  case class MandrillResult(response: Try[HttpResponse], msg: Message) extends Serializable
}

