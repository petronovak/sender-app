package com.senderapp.processing

import akka.event.LoggingAdapter
import com.senderapp.model.{ Message, JsonMessageData }
import com.senderapp.templates.Mustache
import com.senderapp.utils.Utils
import com.typesafe.config._
import scala.collection.JavaConversions._
import scala.io.Source

case class ProcessingRule(criteria: Criteria, trigger: Trigger) {
  def apply(msg: Message, log: LoggingAdapter) = {
    if (criteria(msg)) {
      log.info(s"Applying rule $trigger on $msg")
      trigger(msg)
    } else {
      log.debug(s"Skipping rule with $criteria on $msg, rules: ${criteria.debug(msg)}")
      ProcessingResult(msg)
    }
  }
}

object ProcessingRule {
  def apply(config: Config): ProcessingRule =
    ProcessingRule(new Criteria(config.getConfig("if")), new Trigger(config.getConfig("do")))
}

class Criteria(config: Config) {
  val meta = if (config.hasPath("meta")) {
    mapAsScalaMap(config.getObject("meta").unwrapped()).entrySet()
  } else Map[String, Object]().entrySet()

  val body = if (config.hasPath("body")) {
    mapAsScalaMap(config.getObject("body").unwrapped()).entrySet()
  } else Map[String, Object]().entrySet()

  def apply(msg: Message) = matchMeta(msg) &&
    matchBody(msg)

  def debug(msg: Message): String = {
    s"Meta: ${matchMeta(msg)}, Body: ${matchBody(msg)}"
  }

  def matchMeta(msg: Message): Boolean = meta.forall { e =>
    msg.meta.get(e.getKey).contains(e.getValue) // TODO: create matcher
  }

  def matchBody(msg: Message): Boolean = msg.data match {
    case jsMsg: JsonMessageData =>
      val js = jsMsg.js.asJsObject.fields

      body.forall { e =>
        val opt = js.get(e.getKey).map(Utils.unwrapJson).map(_.toString)
        opt.contains(e.getValue) // TODO: create matcher
      }
    case _ =>
      false
  }

  override def toString = s"Criteria: meta:${meta.mkString(",")}, body:${body.mkString(",")}"
}

class Trigger(config: Config) {

  val service = if (config.hasPath("send-by")) Some(config.getString("send-by")) else None
  val continue = if (config.hasPath("continue")) config.getBoolean("continue") else true

  val otherFields = config.entrySet()
    .filterNot(_.getKey.equals("service"))
    .map(e => (e.getKey, Utils.unwrap(e.getValue)))
    .toMap

  def apply(msg: Message): ProcessingResult = {
    val meta = msg.meta ++ otherFields.map { case (k, v) => (k, loadVars(msg, v)) }
    val updatedMsg = Message(service.getOrElse(msg.service), meta, msg.data)

    ProcessingResult(updatedMsg, send = service.isDefined, continue = continue)
  }

  def loadVars(msg: Message, v: AnyRef): AnyRef = v match {
    case template: String if template.contains("{{") =>
      new Mustache(Source.fromString(template)).render(msg.data.asMap)
    case any => any
  }

  override def toString = s"Trigger: service = $service, fields = ${otherFields.mkString}"
}

case class ProcessingResult(msg: Message, send: Boolean = false, continue: Boolean = true)