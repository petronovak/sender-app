package com.senderapp.model.utils

import com.senderapp.utils.Utils._
import org.scalatest.{ FlatSpec, Matchers }
import spray.json.{ JsNumber, JsString, JsonParser }

/**
 * Created by sergeykhruschak on 6/27/16.
 */
class UtilsJsonSpec extends FlatSpec with Matchers {
  "A Utils" should "find existent jsPath " in {
    JsonParser(
      """{ "body" : {
        |   "height" : 500,
        |   "width" : 30
        | },
        | "meta": "string"
        |}""".stripMargin
    ).pathOpt("body.width") shouldEqual Some(JsNumber(30))
  }

  it should "work correctly for one level path" in {
    JsonParser(
      """{ "body" : { "height" : 500 }, "meta": "string"}"""
    ).pathOpt("meta") shouldEqual Some(JsString("string"))
  }

  it should "accept non existent path" in {
    JsonParser(
      """{ "body" : { "height" : 500 }, "meta": "string"}"""
    ).pathOpt("body.width") shouldEqual None
  }

  it should "search path in empty json" in {
    JsonParser("{}").pathOpt("body.width") shouldEqual None
  }
}
