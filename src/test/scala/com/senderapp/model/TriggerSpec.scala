package com.senderapp.model

import org.scalatest.{FlatSpec, Matchers}
import spray.json.{JsNull, JsObject, JsString}
import com.senderapp.utils.Utils._
/**
  * Created by Sergey Khruschak on 7/14/16.
  */
class TriggerSpec extends FlatSpec with Matchers {

  "A Trigger" should "add meta fields to the message" in {
    val t = Trigger(
      """{
        | "subject":"Test",
        | "template":"mustache: OK {{no_replaced}}",
        | "send-by":"mailgun",
        | "important":true,
        | "destination":"{{email}}"
        |}""".stripMargin)

    val convertedMsg = t(Message("", data = JsObject(
      "email" -> JsString("test@mail.com"),
      "phone" -> JsString("380979473350")
    )))

    val metaJson = convertedMsg.msg.meta

    metaJson.getStringOpt("subject") shouldBe Some("Test")
    metaJson.getStringOpt("destination") shouldBe Some("{{email}}")
    metaJson.getStringOpt("template") shouldBe Some("mustache: OK {{no_replaced}}")
  }

}
