package com.senderapp.utils

import com.typesafe.config._
import spray.json.{JsObject, _}

import scala.collection.JavaConversions._

object Utils {

  implicit def configAsJson(conf: ConfigValue): JsValue = {
    import ConfigValueType._

    conf.valueType() match {
      case OBJECT =>
        JsObject(
          conf.asInstanceOf[ConfigObject].entrySet().toSeq.map { entry =>
            (entry.getKey, configAsJson(entry.getValue))
          }: _*)
      case LIST =>
        JsArray(
          conf.asInstanceOf[ConfigList].map(configAsJson) :_*
        )
      case STRING =>
        JsString(conf.unwrapped().toString)
      case NUMBER =>
        JsNumber(conf.unwrapped().toString)
      case BOOLEAN =>
        JsBoolean(conf.unwrapped().asInstanceOf[Boolean])
      case NULL =>
        JsNull
    }
  }

  def unwrap(v: ConfigValue): AnyRef = v.valueType() match {
    case ConfigValueType.OBJECT =>
      v.asInstanceOf[ConfigObject].entrySet().map(e => (e.getKey, unwrap(e.getValue))).toMap
    case ConfigValueType.LIST =>
      v.asInstanceOf[ConfigList].iterator().map(unwrap).toList
    case _ =>
      v.unwrapped()
  }


  implicit class RichConfigObject(val cfg: ConfigObject) extends AnyVal {
    def getString(key: String, defVal: => String): String = getStringOpt(key).getOrElse(defVal)
    def getStringOpt(key: String): Option[String] = Option(cfg.get(key)).map(_.unwrapped().toString)
    def getBool(key: String, defVal: => Boolean): Boolean = getBoolOpt(key).getOrElse(defVal)
    def getBoolOpt(key: String): Option[Boolean] = Option(cfg.get(key)).map(_.unwrapped().asInstanceOf[Boolean])
  }

  implicit class RichJson(val js: JsValue) extends AnyVal {
    def unwrap: Any = unwrapJson(js)
    def pathOpt(path: String): Option[JsValue] = jsPath(js, path)
    def getString(path: String, defVal: => String): String = pathOpt(path).filter(_.isInstanceOf[JsString]).map(_.asInstanceOf[JsString].value).getOrElse(defVal)
    def getStringOpt(path: String): Option[String] = pathOpt(path).filter(_.isInstanceOf[JsString]).map(_.asInstanceOf[JsString].value)
    def getBool(path: String, defVal: => Boolean = false): Boolean = pathOpt(path).filter(_.isInstanceOf[JsBoolean]).map(_.asInstanceOf[JsBoolean].value).getOrElse(defVal)
    def getBoolOpt(path: String): Option[Boolean] = pathOpt(path).filter(_.isInstanceOf[JsBoolean]).map(_.asInstanceOf[JsBoolean].value)
    def ++(otherVal: JsValue): JsObject = JsObject(js.asJsObject.fields ++ otherVal.asJsObject.fields)
    def mapValues(mapper: JsValue => JsValue): JsValue = mapJson(js, mapper)
  }

  private[this] def unwrapJson(js: JsValue): Any = js match {
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

  private[this] def mapJson(js: JsValue, mapper: JsValue => JsValue): JsValue = js match {
    case obj: JsObject =>
      JsObject(obj.fields.map { case (name, f) => (name, mapJson(f, mapper)) }.toSeq :_*)
    case arr: JsArray =>
      JsArray(arr.elements.map(el => mapJson(el, mapper)))
    case any: JsValue =>
      mapper(any)
  }

  private[this] def jsPath(jsValue: JsValue, path: String): Option[JsValue] = jsValue match {
    case JsObject(fields) if path.contains(".") =>
      val nextPathEntry = path.split("\\.")(0)
      fields.get(nextPathEntry).flatMap(v => jsPath(v, path.stripPrefix(nextPathEntry + ".")))
    case JsObject(fields) => // last path entry
      fields.get(path)
    case _ =>
      None
  }
}
