package com.senderapp.templates

import java.security.InvalidParameterException

import com.senderapp.model.{ JsonMessageData, Message }
import spray.json.JsString

import scala.compat.Platform
import scala.io.Source
import scala.collection._

/**
 * Wrapper class for all supported template engines and sources.
 * !!!Not thread safe, so should not be called from concurrent code!!!
 * Has internal templates cache.
 *
 * Currently supported templates:
 *
 *  * URL – has to be valid URL ending with '.mustache', will load URL contents and compile them as Mustache template
 *  * File – has to have '.mustache' extension, will load file contents and compile them as Mustache template
 *  * inline template – has to start with 'mustache:' and then contain a template
 *  * raw-json – json data will be send as an output of template
 *  * bin-data – will output any data, that was in the 'bin' JSON field in the request
 *
 */
class TemplateEngine {

  private[this] val cache = mutable.HashMap[String, Mustache]()
  final val CACHE_TTL = 120000
  var cacheClearTime = 0L

  def renderBody(msg: Message): Option[String] = {

    if (cacheClearTime < Platform.currentTime) {
      clearCache
    }

    renderBodyCached(msg)
  }

  def renderBodyCached(msg: Message): Option[String] = msg.meta.get("template").flatMap {
    case templateUrl: String if templateUrl.contains("://") && templateUrl.endsWith(".mustache") =>
      Some(cache.getOrElseUpdate(templateUrl, new Mustache(Source.fromURL(templateUrl, "UTF-8"))).render(msg.data.asMap))

    case templateFile: String if templateFile.endsWith(".mustache") =>
      Some(cache.getOrElseUpdate(templateFile, new Mustache(Source.fromFile(templateFile, "UTF-8"))).render(msg.data.asMap))

    case templateInline: String if templateInline.startsWith("mustache:") =>
      val templateData = templateInline.substring("mustache:".length)
      Some(cache.getOrElseUpdate(templateInline, new Mustache(Source.fromString(templateData))).render(msg.data.asMap))

    case "raw-json" =>
      msg.data match {
        case jsonData: JsonMessageData =>
          Some(jsonData.js.compactPrint)
        case _ =>
          throw new InvalidParameterException("Message data can not be converted into JSON")
      }
    case "bin-data" =>
      msg.data match {
        case jsonData: JsonMessageData =>
          Some(jsonData.js.asJsObject.fields("bin").asInstanceOf[JsString].value) // TODO: validation
        case _ =>
          throw new InvalidParameterException("Message data can not be converted into JSON")
      }
    case other =>
      None
  }

  def clearCache = {
    cacheClearTime = Platform.currentTime + CACHE_TTL
    cache.clear()
  }
}
