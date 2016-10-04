package com.senderapp.processing.sms

import java.net.URLEncoder

import akka.http.scaladsl.model.HttpRequest
import com.senderapp.model.Message
import com.senderapp.utils.Utils._

import scala.language.postfixOps

class UnisendSmsSendingActor extends SmsSendingActor {

  override val provider: String = "unisender-sms"

  def buildRequest(msg: Message): HttpRequest = {
    val path = config.getString("path")
    val key = URLEncoder.encode(config.getString("key"), "UTF-8")
    val phones = URLEncoder.encode(msg.meta.getString("destination", config.getString("destination")), "UTF-8")
    val from = URLEncoder.encode(msg.meta.getString("fromName", config.getString("fromName")), "UTF-8")
    val body = URLEncoder.encode(msg.body.getOrElse(config.getString("body")), "UTF-8")

    val url = s"$path?format=json&api_key=$key&phone=$phones&sender=$from&text=$body"
    log.info(s"Sending $provider request: ${config.getString("host")}:${config.getString("port")}$url")
    HttpRequest(uri = url)
  }

}
