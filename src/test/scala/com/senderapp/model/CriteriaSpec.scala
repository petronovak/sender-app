package com.senderapp.model

import com.typesafe.config.{ Config, ConfigFactory }
import org.scalatest._
import spray.json._

/**
 * Created by sergeykhruschak on 6/24/16.
 */
class CriteriaSpec extends FlatSpec with Matchers {

  "A Criteria" should "accept empty config" in {
    val crit = Criteria("{}")
    crit.matchMeta("""{}""".parseJson) shouldEqual true
    crit.matchBody("""{}""".parseJson) shouldEqual true
  }

  it should "accept and search complex meta config" in {
    val crit = Criteria(
      """{ meta: {
        |body { width = 24, height = 30, attr = { "$exists": true }}
        |object.id = 175
        |type=global
        |info.attr = { "$exists": true },
        |body.logic = {"$exists" : false}
        |}}""".stripMargin)

    crit.matchMeta("""{}""".parseJson) shouldEqual false
  }

  it should "match meta regardless the data type" in {
    val crit = Criteria(
      """{ meta: { width = 24, flag = "true" } }""".stripMargin)
    crit.matchMeta("""{ "width" : 24, "flag": true }""".parseJson) shouldEqual true
  }

  it should "match meta with exists" in {
    val crit = Criteria(
      """{ meta: { width = 24, attr = { "$exists": true } } }""".stripMargin)
    crit.matchMeta("""{ "width" : "24", "attr": 50 }""".parseJson) shouldEqual true
  }

  it should "match numbers by a regular expressions" in {
    val crit = Criteria(
      """{ meta: { name = { "$regex": "\\d{2}" } } }""".stripMargin)
    crit.matchMeta("""{ "name": 50 }""".parseJson) shouldEqual true
    crit.matchMeta("""{ "name": 501 }""".parseJson) shouldEqual false
  }
}
