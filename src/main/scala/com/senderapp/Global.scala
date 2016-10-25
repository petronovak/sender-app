package com.senderapp

import java.io.File

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.typesafe.config.{ Config, ConfigFactory }
import org.slf4j.LoggerFactory

import scala.io.Source

object Global {

  private[this] val log = LoggerFactory.getLogger("global")
  private[this] var config = configLoad(ConfigFactory.load())
  private[this] var lastConfigUpdateTime = 0L

  implicit val system = ActorSystem("sender", config)
  implicit val materializer = ActorMaterializer()(system)
  implicit val executionContext = system.dispatcher

  def globalConfig: Config = config

  def updateConfig: Boolean = {
    val oldConfig = config
    config = configLoad(oldConfig)
    !config.equals(oldConfig)
  }

  private def configLoad(fallbackCfg: => Config): Config = {
    Option(System.getProperty("config")) orElse Option(System.getenv("config")) match {
      case Some(url) if url.contains("://") =>
        log.trace(s"Reading configuration from URL: $url")
        val source = Source.fromURL(url, "UTF-8")
        ConfigFactory.parseString(source.mkString).withFallback(ConfigFactory.defaultReference()).resolve()

      case Some(fileName) =>
        val file = new File(fileName)

        if (file.lastModified() > lastConfigUpdateTime) {
          log.trace(s"Reading configuration from file: $fileName")
          lastConfigUpdateTime = file.lastModified()
          ConfigFactory.parseFile(file).withFallback(ConfigFactory.defaultReference()).resolve()
        } else {
          fallbackCfg
        }
      case None =>
        log.trace(s"Using default configuration")
        ConfigFactory.load().resolve()
    }

  }
}
