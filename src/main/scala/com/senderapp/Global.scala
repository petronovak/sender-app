package com.senderapp

import java.io.File
import java.net.URL

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.typesafe.config.{ Config, ConfigFactory }
import org.slf4j.LoggerFactory

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
        log.info(s"Reading configuration from URL: $url")
        ConfigFactory.parseURL(new URL(url)).withFallback(ConfigFactory.defaultReference())

      case Some(fileName) =>
        val file = new File(fileName)

        if (file.lastModified() > lastConfigUpdateTime) {
          log.info(s"Reading configuration from file: $fileName")
          lastConfigUpdateTime = file.lastModified()
          ConfigFactory.parseFile(file).withFallback(ConfigFactory.defaultReference())
        } else {
          fallbackCfg
        }
      case None =>
        log.info(s"Using default configuration")
        ConfigFactory.load()
    }

  }
}
