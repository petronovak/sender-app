package com.senderapp.processing.sms

import akka.http.scaladsl.model.headers.{Authorization, BasicHttpCredentials}
import akka.http.scaladsl.model.{FormData, HttpMethods, HttpRequest}
import com.senderapp.model.Message
import com.senderapp.processing.AbstractSendingActor
import com.senderapp.utils.Utils._

import scala.language.postfixOps

/**
  * Implementation of a clint for Twilio.com.
  * See: https://www.twilio.com/docs/quickstart/java/sms
  * @author Sergey Khruschak
  * @author Yaroslav Derman
  */
class TwilioSmsSendingActor extends AbstractSendingActor {

  val provider: String = "twilio"

  def buildRequest(msg: Message): HttpRequest = {

    val path = config.getString("path")
    val accountSid = config.getString("accountSid")
    val token = config.getString("token")

    val phone = msg.meta.getString("destination", config.getString("destination"))
    val from = msg.meta.getString("fromName", config.getString("fromName"))
    val body = msg.body.getOrElse(config.getString("body"))

    val url = s"$path/Accounts/$accountSid/Messages"
    log.info(s"Sending $provider request: ${config.getString("host")}:${config.getString("port")}$url")

    HttpRequest(uri = url,
      method = HttpMethods.POST,
      entity = FormData("From" -> from, "To" -> phone, "Body" -> body).toEntity
    ).withHeaders(Authorization(BasicHttpCredentials(accountSid, token)))
  }

}
