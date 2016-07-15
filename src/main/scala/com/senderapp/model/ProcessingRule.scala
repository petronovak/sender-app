package com.senderapp.model

import akka.event.LoggingAdapter
import com.senderapp.model.matchers.{ Matcher, Matchers }
import com.senderapp.templates.Mustache
import com.senderapp.utils.Utils._
import com.typesafe.config._
import spray.json.{ JsObject, JsString, JsValue }

import scala.collection.JavaConversions._
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
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
  val metaMatchers = if (config.hasPath("meta")) {
    parseMatchers(mutable.ListBuffer[Matcher](), "", config.getConfig("meta"), config.getObject("meta")).toList
  } else List[Matcher]()

  val bodyMatchers = if (config.hasPath("body")) {
    parseMatchers(mutable.ListBuffer[Matcher](), "", config.getConfig("body"), config.getObject("body")).toList
  } else List[Matcher]()

  def apply(msg: Message) = matchMeta(msg.meta) && matchBody(msg.data)

  def matchMeta(js: JsValue) = matches(metaMatchers, js)
  def matchBody(js: JsValue) = matches(bodyMatchers, js)

  def debug(msg: Message): String = {
    s"Meta: ${matchMeta(msg.meta)}, Body: ${matchBody(msg.data)}"
  }

  def matches(matchers: List[Matcher], js: JsValue): Boolean = matchers.forall(_.matches(js))

  private def parseMatchers(buffer: ListBuffer[Matcher], path: String, conf: Config, c: ConfigValue): ListBuffer[Matcher] = {
    Matchers.fromConfig(c, path, conf) match {
      case Some(matcher) =>
        buffer += matcher
      case None =>
        c match {
          case obj: ConfigObject =>
            obj.entrySet().foreach { entry =>
              val subPath = path + (if (path.nonEmpty) "." else "") + entry.getKey
              parseMatchers(buffer, subPath, conf, entry.getValue)
            }

          case v =>
            throw new RuntimeException("Can't find matcher for value: " + v + " at path: " + path)
        }
        c
    }

    buffer
  }

  override def toString = s"Criteria: meta:${metaMatchers.mkString(",")}, body:${bodyMatchers.mkString(",")}"
}

object Criteria {
  def apply(config: Config): Criteria = new Criteria(config)
  def apply(configStr: String): Criteria = new Criteria(ConfigFactory.parseString(configStr))
}

class Trigger(config: Config) {
  val service = if (config.hasPath("send-by")) Some(config.getString("send-by")) else None
  val continue = if (config.hasPath("continue")) config.getBoolean("continue") else true

  val otherFields: JsObject = config.root().asJsObject

  def apply(msg: Message): ProcessingResult = {
    val meta = msg.meta ++ JsObject(otherFields.fields)
    val updatedMsg = Message(service.getOrElse(msg.service), meta, msg.data)

    ProcessingResult(updatedMsg, send = service.isDefined, continue = continue)
  }



  override def toString = s"Trigger: service = $service, fields = $otherFields"
}

object Trigger {
  def apply(config: Config): Trigger = new Trigger(config)
  def apply(configStr: String): Trigger = new Trigger(ConfigFactory.parseString(configStr))
}

case class ProcessingResult(msg: Message, send: Boolean = false, continue: Boolean = true)