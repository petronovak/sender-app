package com.senderapp.model

import com.senderapp.templates.TemplateEngine
import org.scalatest.{ FlatSpec, Matchers }
import spray.json.{ JsNumber, JsObject, JsString }

/**
 * Created by sergeykhruschak on 6/29/16.
 */
class TemplateRenderingSpec extends FlatSpec with Matchers {

  "A template engine" should "render a sectioned message" in {
    val tpl = new TemplateEngine()

    val meta = JsObject(
      "template" -> JsString("mustache:Inline template test: {{name}}, phone: {{#data}}{{phone}}, no: {{no}}{{/data}}")
    )

    val body = JsObject("name" -> JsString("user"),
      "data" -> JsObject(
        "phone" -> JsString("+380979473350"), "no" -> JsNumber(6)
      )
    )

    val msg = new Message("template-test", meta, body)

    tpl.renderBody(msg) shouldEqual Some("Inline template test: user, phone: +380979473350, no: 6")
  }
}
