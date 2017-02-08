package com.senderapp.processing.email

import java.util.Properties
import javax.mail.internet._
import javax.mail.{PasswordAuthentication, Session, Transport, Message => JavaMail}

import akka.actor.{Actor, ActorLogging}
import com.senderapp.model.{Events, Message}
import com.senderapp.utils.Utils._
import com.typesafe.config.{Config, ConfigFactory}


/**
  * Created by faiaz on 31.01.17.
  */
class SmtpSendingActor extends Actor with ActorLogging {

  private val provider = "smtp"

  private var config: Config = _

  private val login: String = config.getString(s"$provider.login")
  private val password: String = config.getString(s"$provider.password")
  private val port: String = config.getString(s"$provider.port")
  private val host: String = config.getString(s"$provider.host")

  private val defaultDestination: String = config.getString(s"$provider.destination")
  private val defaultSubject: String = config.getString(s"$provider.subject")
  private val defaultText: String = config.getString(s"$provider.text")

  private val props: Properties = createProperties

  private val senderAuthenticator = {
    log.info(s"Creating Authenticator for $login")
    new javax.mail.Authenticator() {
      override protected def getPasswordAuthentication: PasswordAuthentication = {
        new PasswordAuthentication(login, password)
      }
    }
  }

  private val session: Session = Session.getInstance(props, senderAuthenticator)

  override def receive = {
    case msg: Message =>
      log.info(s"Receive msg $msg for $provider")
      sendMail(msg)
    case Events.Configure(_, newConfig) =>
      configure(newConfig)
    case other => log.info(s"Unknown message $other")
  }

  private def sendMail(msg: Message): Unit = {
    log.info("Creating session")

    val message = new MimeMessage(session)
    val textContent = msg.meta.getStringOpt("text").getOrElse(defaultText)
    val subject = msg.meta.getStringOpt("subject").getOrElse(defaultSubject)
    val destination = msg.meta.getStringOpt("destination").getOrElse(defaultDestination)

    message.setFrom(new InternetAddress(login))
    message.setRecipient(JavaMail.RecipientType.TO, new InternetAddress(destination))
    message.setSubject(subject)
    message.setText(textContent)

    Transport.send(message)
    log.info(s"Mail sent from: $login to: $destination with subject: $subject")
  }

  private def createProperties: Properties = {
    log.info("Creating properties")
    val resProp = System.getProperties
      resProp.put("mail.smtp.auth", "true")
      resProp.put("mail.smtp.starttls.enable", "true")
      resProp.put("mail.smtp.port", port)
      resProp.put("mail.smtp.host", host)
    resProp
  }

  private def configure(newConfig: Config) = {
    config = newConfig.withFallback(ConfigFactory.defaultReference().getConfig(provider))
    log.info(s"Configure $provider sending actor")
  }
}
