package com.senderapp

import akka.actor.Props
import akka.pattern.BackoffSupervisor

import com.senderapp.model.Events
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory
import scala.collection.JavaConversions._
import scala.concurrent.duration._

object Application extends App {

  import Global._

  val actorsConfigList = globalConfig.getConfigList("akka.actors")

  val actorsList = actorsConfigList map { actorConf =>
    val className = actorConf.getString("class")
    val name = actorConf.getString("name")
    val configName = if (actorConf.hasPath("config")) actorConf.getString("config") else name
    val props = Props(Class.forName(className))

    val supervisor = BackoffSupervisor.props(
      props,
      childName = name + "_inst",
      minBackoff = 3.seconds,
      maxBackoff = 30.seconds,
      randomFactor = 0.2)

    (name, configName, system.actorOf(supervisor, name))
  }

  // schedule configuration source reload with actors reconfiguration
  system.scheduler.schedule(30 seconds, 30 seconds) {
    if (Global.updateConfig) {
      reconfigureActors
    }
  }

  reconfigureActors

  def reconfigureActors = actorsList.foreach {
    case (name, configName, actor) =>
      val conf = configName match {
        case ""                                              => Global.globalConfig
        case cfgName if Global.globalConfig.hasPath(cfgName) => Global.globalConfig.getConfig(cfgName)
        case unknownCfg =>
          LoggerFactory.getLogger("main").warn(s"No configuration found for actor: $name at name $unknownCfg, applying empty config")
          ConfigFactory.empty()
      }

      actor ! Events.Configure(name, conf)
  }
}
