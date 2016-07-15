package com.senderapp.model

import com.senderapp.templates.TemplateEngine
import org.scalatest.{ FlatSpec, Matchers }
import spray.json.{ JsNumber, JsObject, JsString }
import com.senderapp.utils.Utils._

/**
 * Created by sergeykhruschak on 6/29/16.
 */
class TemplateRenderingSpec extends FlatSpec with Matchers {

  "A template engine" should "render a sectioned message" in {
    val tpl = new TemplateEngine()

    val meta = JsObject(
      "destination" -> JsString("{{email}}"),
      "sms" -> JsString("{{#data}}{{phone}}{{/data}}"),
      "template" -> JsString("mustache:Inline template test: {{name}}, phone: {{#data}}{{phone}}, no: {{no}}{{/data}}")
    )

    val body = JsObject("name" -> JsString("user"),
      "data" -> JsObject(
        "phone" -> JsString("+100914314300"), "no" -> JsNumber(6)
      ),
      "email" -> JsString("some@mail.com")
    )

    val msg = new Message("template-test", meta, body)

    val renderedMsg = tpl.renderTemplates(msg)
    renderedMsg.body shouldEqual Some("Inline template test: user, phone: +100914314300, no: 6")
    renderedMsg.meta.getStringOpt("destination") shouldEqual Some("some@mail.com")
    renderedMsg.meta.getStringOpt("sms") shouldEqual Some("+100914314300")
  }
}
