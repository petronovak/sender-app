package actor

import java.util.Properties
import javax.mail.{Folder, Session}

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import com.senderapp.model.Message
import com.senderapp.processing.email.SmtpSendingActor
import de.saly.javamail.mock2.MockMailbox
import org.scalatest.{FlatSpecLike, Matchers}
import spray.json.{JsObject, JsString}

/**
  * Created by faiaz on 31.01.17.
  */
class SmtpSendingActorSpec extends TestKit(ActorSystem("test"))
  with FlatSpecLike
  with ImplicitSender
  with Matchers {

  val smtp = system.actorOf(Props(new SmtpSendingActor))
  val testMail = "unknownUser@unknown.com"

  val mailBox = MockMailbox.get(testMail)
  val mailBoxFolder = mailBox.getInbox
  val session = Session.getInstance(new Properties())

  //todo: fix tests
  "SmtpActor" should "send mail" in {

    smtp ! Message(
      service = "testService",
      meta = JsObject("destination" -> JsString(testMail), "subject" -> JsString("Test")))

    Thread.sleep(1000)

    val store = session.getStore("mock_imap")
    store.connect(testMail, null)
    val inbox = store.getFolder("INBOX")
    inbox.open(Folder.READ_ONLY)

    inbox.getMessageCount shouldEqual 1
  }
}
