package com.senderapp.processing

import akka.actor.{ Actor, ActorLogging }
import com.senderapp.model.{ Events, Message }
import com.senderapp.templates.TemplateEngine
import com.typesafe.config.Config
import scala.collection.JavaConversions._

class MessagesRoutingActor extends Actor with ActorLogging {

  var rules: List[ProcessingRule] = List()

  final val templateEngine = new TemplateEngine()
  final val interruption = new InterruptedException

  override def receive: Receive = {
    case Events.Configure(name, config) =>
      log.debug(s"Loading rules configuration ${config.getConfigList("rules")}")
      rules = loadRules(config)
      templateEngine.clearCache
    case msg: Message =>
      log.info(s"$msg")

      try {
        rules.foldLeft(msg) {
          case (curMsg, rule) =>
            val processingResult = rule(curMsg, log)

            if (processingResult.send) {
              val updatedMsg = renderBody(processingResult.msg)

              log.info(s"Routing message $updatedMsg to ${updatedMsg.service} service")

              context.actorSelection(s"/user/${updatedMsg.service}") ! updatedMsg
            }

            if (!processingResult.continue) {
              throw interruption
            }

            processingResult.msg
        }

      } catch {
        case intEx: InterruptedException =>
        // do nothing
      }

  }

  def loadRules(config: Config) =
    config.getConfigList("rules").map(ProcessingRule(_)).toList

  def renderBody(msg: Message) = {
    val bodyOpt = templateEngine.renderBody(msg)
    bodyOpt.map(body => msg.copy(body = Some(body))).getOrElse(msg)
  }
}
