package controllers

import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.JsArray
import play.api.test.CSRFTokenHelper._
import play.api.test.FakeRequest
import play.api.test.Helpers._

class AppSpec extends PlaySpec with GuiceOneAppPerSuite {

  private def get(url: String) = route(app, FakeRequest(GET, url)).get
  private def post(url: String, form: (String, String)*) =
    route(app, FakeRequest(POST, url).withFormUrlEncodedBody(form: _*).withCSRFToken).get
  private def worklist(html: String) = html.substring(html.indexOf("<ol class=\"worklist\">"), html.indexOf("</ol>"))

  "Who to help first" should {
    "show this week's list, highest risk first, limited to capacity" in {
      val res = get("/?capacity=2")
      status(res) mustBe OK
      val week = worklist(contentAsString(res))
      "<li>".r.findAllIn(week).size mustBe 2
      week.indexOf("/homes/P01") must be < week.indexOf("/homes/P09")
    }

    "leave homes with an action in progress off this week's list" in {
      worklist(contentAsString(get("/?capacity=10"))) must not include "/homes/P10"
    }

    "return 404 for an unknown home" in {
      status(get("/homes/NOPE")) mustBe NOT_FOUND
    }

    "explain a silent tenant's home and hide health details without consent" in {
      val html = contentAsString(get("/homes/P02"))
      html must include("Not heard from")
      html must include("welcome-check call")
      html must include("Not shared - no consent")
    }
  }

  "API" should {
    "return at-risk homes sorted by score without personal details" in {
      val res = get("/api/homes/at-risk?minScore=25")
      status(res) mustBe OK
      val scores = contentAsJson(res).as[JsArray].value.map(j => (j \ "score").as[Int])
      scores must not be empty
      scores mustBe scores.sorted.reverse
      all(scores) must be >= 25
      contentAsString(res) must not include "Amina Khan"
    }
  }

  "Tenant report" should {
    "reject an unknown home reference" in {
      val res = post("/report", "homeId" -> "X999", "issue" -> "mould", "severity" -> "2", "description" -> "")
      status(res) mustBe BAD_REQUEST
      contentAsString(res) must include("Enter your home reference")
    }

    "accept a report with consent and show it to staff" in {
      val res = post("/report", "homeId" -> "p04", "issue" -> "mould", "severity" -> "3",
        "description" -> "Mould behind wardrobe", "breathingConsent" -> "true")
      status(res) mustBe SEE_OTHER
      redirectLocation(res) mustBe Some("/report/thanks")
      val html = contentAsString(get("/homes/P04"))
      html must include("Mould behind wardrobe")
      html must include("Breathing condition: Yes")
    }
  }

  "Actions and outcomes" should {
    "record an action, then its outcome, and show it on the 'Did it work?' page" in {
      status(post("/homes/P05/actions", "kind" -> "energy-advice", "notes" -> "Leaflet and call")) mustBe SEE_OTHER
      contentAsString(get("/homes/P05")) must include("Did it work?")
      status(post("/homes/P05/actions/4/outcome", "result" -> "improved", "notes" -> "Tenant using heating better")) mustBe SEE_OTHER
      val html = contentAsString(get("/outcomes"))
      html must include("Energy advice")
      html must include("Tenant using heating better")
    }

    "refuse an outcome for an action that belongs to another home" in {
      status(post("/homes/P04/actions/1/outcome", "result" -> "resolved", "notes" -> "")) mustBe NOT_FOUND
    }

    "refuse an action with no type chosen" in {
      val res = post("/homes/P04/actions", "kind" -> "", "notes" -> "")
      status(res) mustBe SEE_OTHER
      flash(res).get("error") mustBe Some("Choose an action")
    }
  }
}
