package com.senderapp.model

import com.senderapp.utils.Utils
import spray.json._

case class Message(service: String, meta: Map[String, AnyRef], data: MessageData, body: Option[String] = None) extends Serializable {}

sealed trait MessageData extends Serializable {
  def asMap: Map[String, Any]
}

case class JsonMessageData(js: JsValue) extends MessageData {
  lazy val asMap = Utils.unwrapJson(js).asInstanceOf[Map[String, Any]] // TODO: check assumption

}
