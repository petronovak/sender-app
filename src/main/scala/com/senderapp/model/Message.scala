package com.senderapp.model

import com.senderapp.utils.Utils._
import spray.json._

case class Message(service: String, meta: JsValue, data: JsValue, body: Option[String] = None) extends Serializable {
  lazy val dataAsMap = data.unwrap.asInstanceOf[Map[String, Any]]
  lazy val metaAsMap = meta.unwrap.asInstanceOf[Map[String, Any]]

  def asTemplateData = dataAsMap ++ Map("meta" -> metaAsMap)
}
