package com.senderapp.processing.email

import akka.http.javadsl.model.HttpEntities
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.model._
import com.senderapp.model.Message
import com.senderapp.processing.AbstractSendingActor
import com.senderapp.utils.Utils._
import spray.json._

import scala.concurrent.duration._

class MandrillSendingActor extends AbstractSendingActor {

  val provider: String = "mandrill"
  override val timeout = 1000.millis

  def buildRequest(msg: Message) = {
    //val tmplField = msg.meta.get("template").map(_.toString).getOrElse(config.getString("template"))

    val json = JsObject(
      "key" -> JsString(config.getString("key")),
      // "template_name" -> JsString(tmplField),
      // "template_content" -> JsArray(tplData),
      "message" -> toMandrillMessage(msg)
    )

    val entity = HttpEntities.create(MediaTypes.`application/json`.toContentType, json.compactPrint)
    RequestBuilding.Post(config.getString("path")).withEntity(entity)
  }

  def toMandrillMessage(msg: Message): JsObject = {
    val fromEmail = msg.meta.getStringOpt("fromEmail").getOrElse(config.getString("fromEmail"))
    val fromName = msg.meta.getStringOpt("fromName").getOrElse(config.getString("fromName"))
    val destination = msg.meta.getStringOpt("destination").getOrElse(config.getString("destination"))
    val subject = msg.meta.getStringOpt("subject").getOrElse(config.getString("subject"))

    //val headers = headersConf ++ msg.meta.get("headers").map(_.asInstanceOf[List[Map[String, String]]]).getOrElse(List())
    val headersJs = headersConf.map { m =>
      val item = m.head
      (item._1, JsString(item._2))
    }

    val tplData = JsArray(msg.data.asJsObject.fields.toSeq.map {
      case (k, v) => JsObject(
        "key" -> JsString(k),
        "content" -> v
      )
    }: _*)

    JsObject(
      "subject" -> JsString(subject),
      "from_email" -> JsString(fromEmail),
      "from_name" -> JsString(fromName),
      "important" -> JsBoolean(msg.meta.getBool("important")),
      "to" -> JsArray(
        JsObject("email" -> JsString(destination))
      ),
      "headers" -> JsObject(headersJs: _*),
      "merge_language" -> JsString("handlebars"),
      "global_merge_vars" -> tplData,
      "html" -> msg.body.map(JsString(_)).getOrElse(JsNull)
    )
  }

}

