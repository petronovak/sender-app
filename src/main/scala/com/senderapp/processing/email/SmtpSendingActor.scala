package com.senderapp.processing.email

import java.util.Properties
import javax.mail.internet._
import javax.mail.{ Authenticator, PasswordAuthentication, Session, Transport, Message => JavaMail }

import akka.actor.{ Actor, ActorLogging }
import com.senderapp.model.{ Events, Message }
import com.senderapp.utils.Utils._
import com.typesafe.config.{ Config, ConfigFactory }

/**
 * Created by faiaz on 31.01.17.
 */
class SmtpSendingActor extends Actor with ActorLogging {
  private final val provider = "smtp"
  private var config: Config = _

  private var login: String = _
  private var loginAddr: InternetAddress = _
  private var password: String = _
  private var port: String = _
  private var host: String = _

  private var defaultDestination: String = _
  private var defaultSubject: String = _
  private var defaultText: String = _

  private var props: Properties = _

  private var senderAuthenticator: Authenticator = _

  private var session: Session = _

  override def receive = {
    case msg: Message =>
      try {
        sendMail(msg)
      } catch {
        case ex: Exception =>
          log.error(ex, s"Failed to send a message $msg")
      }
    case Events.Configure(_, newConfig) =>
      configure(newConfig)
    case other => log.warning(s"Unknown message $other")
  }

  private def sendMail(msg: Message): Unit = {
    val message = new MimeMessage(session)
    val textContent = msg.body.getOrElse(defaultText)
    val subject = msg.meta.getStringOpt("subject").getOrElse(defaultSubject)
    val destination = msg.meta.getStringOpt("destination").getOrElse(defaultDestination)

    val configuredMsg = createMessage(message, subject, textContent, destination)

    Transport.send(configuredMsg)
    log.info(s"Mail sent from: $login to: $destination with subject: $subject")
  }

  private def createMessage(msg: MimeMessage, subject: String, textContent: String, destination: String): MimeMessage = {
    msg.setFrom(loginAddr)
    msg.setRecipient(JavaMail.RecipientType.TO, new InternetAddress(destination))
    msg.setSubject(subject)
    msg.setText(textContent, "utf-8", "html")
    msg
  }

  private def createProperties: Properties = {
    val resProp = System.getProperties
    resProp.put("mail.smtp.auth", "true")
    resProp.put("mail.smtp.starttls.enable", "true")
    resProp.put("mail.smtp.port", port)
    resProp.put("mail.smtp.host", host)
    resProp
  }

  private def createAuthenticator: Authenticator = new javax.mail.Authenticator() {
    override protected def getPasswordAuthentication: PasswordAuthentication = {
      new PasswordAuthentication(login, password)
    }
  }

  private def createSession = Session.getInstance(props, senderAuthenticator)

  private def configure(newConfig: Config) = {
    config = newConfig.withFallback(ConfigFactory.defaultReference().getConfig(provider))

    login = config.getString("login")
    loginAddr = new InternetAddress(login)
    password = config.getString("password")
    port = config.getString("port")
    host = config.getString("host")

    defaultDestination = config.getString("destination")
    defaultSubject = config.getString("subject")
    defaultText = config.getString("text")

    props = createProperties
    senderAuthenticator = createAuthenticator
    session = createSession
  }
}
