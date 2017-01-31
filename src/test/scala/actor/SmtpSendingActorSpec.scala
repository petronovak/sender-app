package actor

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import com.senderapp.model.Message
import com.senderapp.processing.email.SmtpSendingActor
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
  val testMail = "fayaz.sanaulla@gmail.com"


  //todo: fix tests
  "SmtpSendingActor" should "send mail" in {

    smtp ! Message(
      service = "testService",
      meta = JsObject("destination" -> JsString(testMail), "subject" -> JsString("Test"))
    )

    Thread.sleep(3000)
  }
}
