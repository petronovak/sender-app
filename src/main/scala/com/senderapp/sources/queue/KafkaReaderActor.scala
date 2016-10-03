package com.senderapp.sources.queue

import java.util

import akka.actor.{Actor, ActorLogging}
import akka.kafka.scaladsl.Consumer
import akka.kafka.{ConsumerMessage, ConsumerSettings, Subscriptions}
import akka.stream.scaladsl._
import com.senderapp.Global
import com.senderapp.model.{Events, Message}
import com.senderapp.utils.Utils._
import org.apache.kafka.common.serialization.{ByteArrayDeserializer, Deserializer}
import spray.json.{JsObject, JsString, JsValue, JsonParser}

import scala.concurrent.duration._
import scala.language.postfixOps

class KafkaReaderActor extends Actor with ActorLogging {

  import Global._

  var meta = JsObject()
  var consumerControl: Option[Consumer.Control] = None
  val processor = context.actorSelection("/user/messages-router")

  override def receive: Receive = {
    case Events.Configure(name, newConfig) =>
      meta = JsObject("source" -> JsString(name))
      stopConsumer()

      if (newConfig.hasPath("brokerList")) {
        val brokers = newConfig.getString("brokerList")
        val topic = newConfig.getString("topicName")
        val group = newConfig.getString("groupId")

        initReader(brokers, topic, group)
      }
    case e =>
      log.error(s"Unknown event: $e")
  }

  def initReader(brokers: String, topic: String, group: String) {
    log.info(s"Starting the kafka reader on $brokers, topic: $topic, group: $group")

    val consumerSettings = ConsumerSettings(context.system, new ByteArrayDeserializer, new MessageDeserializer())
      .withCommitTimeout(1200 milliseconds)
      .withBootstrapServers(brokers)
      .withGroupId(group)

    consumerControl = Some(Consumer.committableSource(consumerSettings, Subscriptions.topics(topic))
    .toMat(Sink.foreach[ConsumerMessage.CommittableMessage[Array[Byte], JsValue]](processMessage))(Keep.left)
    .run())
  }

  def processMessage(msg: ConsumerMessage.CommittableMessage[Array[Byte], JsValue]) = {
    val record = msg.record
    val completeMeta = if (record.key() != null) {
      meta ++ JsObject(
        "topic" -> JsString(record.topic()), "key" -> JsString(new String(record.key()))
      )
    } else {
      meta ++ JsObject("topic" -> JsString(record.topic()))
    }

    val jsMsg = Message("trash", completeMeta, record.value())
    processor ! jsMsg
  }

  private def stopConsumer() = {
    consumerControl.foreach { control =>
      log.info(s"Stopping kafka consumer")
      control.shutdown().recover {
        case re: Exception =>
          log.warning("Error stopping offset sink:", re)
      }
    }
  }

  override def postStop() {
    stopConsumer()
    super.postStop()
  }
}

class MessageDeserializer extends Deserializer[JsValue] {
  override def configure(configs: util.Map[String, _], isKey: Boolean) {}

  override def close() {}

  override def deserialize(topic: String, data: Array[Byte]) = JsonParser(data)
}