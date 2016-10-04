package com.senderapp.processing.sms

import java.net.URLEncoder

import akka.http.scaladsl.model.{HttpMethods, HttpRequest}
import com.senderapp.model.Message
import com.senderapp.utils.Utils
import com.senderapp.utils.Utils._

/**
 * Implementation of a clint for a smsc.ua.
 * See: http://smsc.ua/api/http/
 */
class SmscSendingActor extends SmsSendingActor {

  val provider: String = "smsc"

  def buildRequest(msg: Message): HttpRequest = {

    val path = config.getString("path")
    val login = config.getString("login")
    val password = URLEncoder.encode(config.getString("password"), "UTF-8")

    val phones = URLEncoder.encode(msg.meta.getString("destination", config.getString("destination")), "UTF-8")
    val from = URLEncoder.encode(msg.meta.getString("fromName", config.getString("fromName")), "UTF-8")
    val body = URLEncoder.encode(msg.body.getOrElse(config.getString("body")), "UTF-8")

    // http://smsc.ua/sys/send.php?login=<login>&psw=<password>&phones=<phones>&mes=<message>
    val url = s"$path?login=$login&psw=$password&sender=$from&phones=$phones&mes=$body"
    log.info(s"Sending $provider request: ${config.getString("host")}:${config.getString("port")}$url")

    HttpRequest(uri = url, method = HttpMethods.GET)
  }

}
