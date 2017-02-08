package com.senderapp.processing.email

import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.model.headers.{ Authorization, BasicHttpCredentials }
import akka.http.scaladsl.model.{ HttpRequest, _ }
import com.senderapp.model.Message
import com.senderapp.processing.AbstractSendingActor
import com.senderapp.utils.Utils._

import scala.collection.immutable.Seq
import scala.concurrent.duration._

class MailgunSendingActor extends AbstractSendingActor {

  val provider: String = "mailgun"

  override val timeout = 1000.millis

  def buildRequest(msg: Message): HttpRequest = {

    val url = s"/v3/${config.getString("domain")}/messages"

    val fromEmail = msg.meta.getStringOpt("fromEmail").getOrElse(config.getString("fromEmail"))
    val destination = msg.meta.getStringOpt("destination").getOrElse(config.getString("destination"))
    val subject = msg.meta.getStringOpt("subject").getOrElse(config.getString("subject"))

    //val headers = headersConf ++ msg.meta.get("headers").map(_.asInstanceOf[List[Map[String, String]]]).getOrElse(List())
    val mailgunHeaders = headersConf.map { m =>
      val item = m.head
      ("h:" + item._1) -> item._2
    }

    log.debug(s"Mailgun request: from: $fromEmail, to: $destination, subject: $subject")

    val data = Seq("from" -> fromEmail, "to" -> destination, "subject" -> subject, "html" -> msg.body.getOrElse(""))

    val entity = FormData(data ++ mailgunHeaders: _*).toEntity

    val authHeaders = Seq(Authorization(BasicHttpCredentials("api", config.getString("key"))))

    RequestBuilding.Post(url).withHeadersAndEntity(authHeaders, entity)
  }

}

