package com.senderapp.processing.email

import java.util.Properties
import javax.mail.internet._
import javax.mail.{PasswordAuthentication, Session, Transport, Message => JavaMail}

import akka.actor.{Actor, ActorLogging}
import com.senderapp.model.Message
import com.senderapp.utils.Utils._
import com.typesafe.config.{Config, ConfigFactory}


/**
  * Created by faiaz on 31.01.17.
  */
class SmtpSendingActor extends Actor with ActorLogging {

  val provider = "smtp"
  val config: Config = ConfigFactory.load()

  val from: String = config.getString(s"$provider.login")
  val password: String = config.getString(s"$provider.password")
  val port: String = config.getString(s"$provider.port")
  val host: String = config.getString(s"$provider.host")

  val defaultDestination: String = config.getString(s"$provider.destination")
  val defaultSubject: String = config.getString(s"$provider.subject")
  val defaultText: String = config.getString(s"$provider.text")

  val props: Properties = createProperties

  private val senderAuthenticator = {
    log.info(s"Creating Authenticator for $from")
    new javax.mail.Authenticator() {
      override protected def getPasswordAuthentication: PasswordAuthentication = {
        new PasswordAuthentication(from, password)
      }
    }
  }

  override def receive = {
    case msg: Message =>
      log.info(s"Receive msg $msg for $provider")
      sendMail(msg)
    case other => log.info(s"Unknown message $other")
  }

  private def sendMail(msg: Message): Unit = {
    log.info("Creating session")
    val session = Session.getInstance(props, senderAuthenticator)

    val message = new MimeMessage(session)
    val textContent = msg.meta.getStringOpt("text").getOrElse(defaultText)
    val subject = msg.meta.getStringOpt("subject").getOrElse(defaultSubject)
    val destination = msg.meta.getStringOpt("destination").getOrElse(defaultDestination)

    message.setFrom(new InternetAddress(from))
    message.setRecipient(JavaMail.RecipientType.TO, new InternetAddress(destination))
    message.setSubject(subject)
    message.setText(textContent)

    Transport.send(message)
    log.info(s"Mail sent from: $from to: $destination with subject: $subject")
  }

  private def createProperties = {
    log.info("Creating properties")
    val resProp = System.getProperties
      resProp.put("mail.smtp.auth", "true")
      resProp.put("mail.smtp.starttls.enable", "true")
      resProp.put("mail.smtp.port", port)
      resProp.put("mail.smtp.host", host)
    resProp
  }
}
