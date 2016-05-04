package com.senderapp.utils

import com.typesafe.config._
import spray.json._
import scala.collection.JavaConversions._

object Utils {
  def unwrap(v: ConfigValue): AnyRef = v.valueType() match {
    case ConfigValueType.OBJECT =>
      v.asInstanceOf[ConfigObject].entrySet().map(e => (e.getKey, unwrap(e.getValue))).toMap
    case ConfigValueType.LIST =>
      v.asInstanceOf[ConfigList].iterator().map(unwrap).toList
    case _ =>
      v.unwrapped()
  }

  def unwrapJson(js: JsValue): Any = js match {
    case obj: JsObject =>
      obj.fields.map { case (name, f) => (name, unwrapJson(f)) }
    case arr: JsArray =>
      arr.elements.map(unwrapJson).toList
    case num: JsNumber =>
      num.value
    case str: JsString =>
      str.value
    case any: JsValue =>
      any.toString()
  }
}
