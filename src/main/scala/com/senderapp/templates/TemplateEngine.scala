package com.senderapp.templates

import java.security.InvalidParameterException

import com.senderapp.model.Message
import spray.json.{JsString, JsValue}

import scala.collection._
import scala.compat.Platform
import com.senderapp.utils.Utils._
import org.slf4j.LoggerFactory

import scala.io.Source

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

  private final val log = LoggerFactory.getLogger("template-engine")
  private[this] val cache = mutable.HashMap[String, Mustache]()
  final val CACHE_TTL = 120000
  var cacheClearTime = 0L

  def renderTemplates(msg: Message): Message = {

    if (cacheClearTime < Platform.currentTime) {
      clearCache
    }

    msg.copy(
      meta = msg.meta.mapValues(js => loadVars(msg, js)),
      body = renderBodyCached(msg)
    )
  }

  private[this] def renderBodyCached(msg: Message): Option[String] = msg.meta.getStringOpt("template").flatMap {
    case templateUrl: String if templateUrl.contains("://") && templateUrl.endsWith(".mustache") =>
      log.debug(s"Mustache URL found: $templateUrl")
      Some(cache.getOrElseUpdate(templateUrl, new Mustache(Source.fromURL(templateUrl, "UTF-8"))).render(msg.asTemplateData))

    case templateFile: String if templateFile.endsWith(".mustache") =>
      log.debug(s"Mustache file found: $templateFile")
      Some(cache.getOrElseUpdate(templateFile, new Mustache(Source.fromFile(templateFile, "UTF-8"))).render(msg.asTemplateData))

    case templateInline: String if templateInline.startsWith("mustache:") =>
      log.debug(s"Inline template found")
      val templateData = if (templateInline.startsWith("mustache:")) templateInline.substring("mustache:".length) else templateInline
      Some(cache.getOrElseUpdate(templateInline, new Mustache(Source.fromString(templateData))).render(msg.asTemplateData))
    case "raw-json" =>
      log.debug(s"Raw json template found")
      Some(msg.data.compactPrint)
    case "bin-data" =>
      log.debug(s"Binary template found")
      Some(msg.data.asJsObject.fields("bin").asInstanceOf[JsString].value) // TODO: validation
    case other =>
      log.debug(s"Not found template match: $other")
      None
  }

  def loadVars(msg: Message, v: JsValue): JsValue = v match {
    case template: JsString if template.value.contains("{{") =>
      JsString(new Mustache(Source.fromString(template.value)).render(msg.asTemplateData))
    case any =>
      any
  }

  def clearCache = {
    cacheClearTime = Platform.currentTime + CACHE_TTL
    cache.clear()
  }
}
