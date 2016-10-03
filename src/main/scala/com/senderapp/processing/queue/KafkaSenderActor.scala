package com.senderapp.processing.queue

import java.util

import akka.actor._
import akka.kafka.ProducerSettings
import akka.kafka.scaladsl.Producer
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Flow, Source}
import com.senderapp.Global
import com.senderapp.model.{Events, Message}
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.{ByteArraySerializer, Serializer}

class KafkaSenderActor extends Actor with ActorLogging {
  import Global._

  var producerActor: Option[ActorRef] = None

  override def receive: Receive = {
    case msg: Message =>
      log.info(s"$msg")
      producerActor.get ! msg

    case Events.Configure(name, newConfig) =>
      stopProducer()

      if (newConfig.hasPath("brokerList") && newConfig.hasPath("topicName")) {
        val brokers = newConfig.getString("brokerList")
        val topic = newConfig.getString("topicName")
        initProducer(brokers, topic)
      }

    case unknown =>
      log.error("Received unknown data: " + unknown)
  }

  def initProducer(brokers: String, topic: String) {
    log.info(s"Starting the kafka writer on $brokers, topic: $topic")

    val producerSettings = ProducerSettings(system, new ByteArraySerializer, new MessageSerializer())
      .withBootstrapServers(brokers)

    producerActor = Some(Flow[Message].map(new ProducerRecord[Array[Byte], Message](topic, _))
      .to(Producer.plainSink(producerSettings))
      .runWith(Source.actorRef[Message](100, OverflowStrategy.fail))
    )

  }

  private def stopProducer() = {
    try {
      producerActor foreach { actor =>
        log.info(s"Stopping kafka producer")
        actor ! PoisonPill
      }
    } catch {
      case re: RuntimeException =>
        log.warning("Error stopping offset sink:", re)
    }
  }

  override def postStop() {
    stopProducer()
    super.postStop()
  }
}

class MessageSerializer extends Serializer[Message] {
  override def configure(configs: util.Map[String, _], isKey: Boolean) {}

  override def serialize(topic: String, msg: Message): Array[Byte] = {
    msg.body.get.getBytes("UTF-8")
  }

  override def close() {}
}