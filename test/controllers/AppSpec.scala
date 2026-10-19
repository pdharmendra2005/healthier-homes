package controllers

import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.JsArray
import play.api.test.CSRFTokenHelper._
import play.api.test.FakeRequest
import play.api.test.Helpers._

class AppSpec extends PlaySpec with GuiceOneAppPerSuite {

  "Dashboard" should {
    "list homes with the highest risk first" in {
      val res = route(app, FakeRequest(GET, "/")).get
      status(res) mustBe OK
      val html = contentAsString(res)
      html must include("Homes at risk")
      html.indexOf("H001") must be < html.indexOf("H004")
    }

    "return 404 for an unknown home" in {
      status(route(app, FakeRequest(GET, "/homes/NOPE")).get) mustBe NOT_FOUND
    }
  }

  "API" should {
    "return at-risk homes sorted by score" in {
      val res = route(app, FakeRequest(GET, "/api/homes/at-risk?minScore=30")).get
      status(res) mustBe OK
      val scores = contentAsJson(res).as[JsArray].value.map(j => (j \ "score").as[Int])
      scores must not be empty
      scores mustBe scores.sorted.reverse
      all(scores) must be >= 30
    }
  }

  "Tenant report" should {
    "reject an unknown home reference" in {
      val req = FakeRequest(POST, "/report")
        .withFormUrlEncodedBody("homeId" -> "X999", "issue" -> "mould", "severity" -> "2", "description" -> "")
        .withCSRFToken
      val res = route(app, req).get
      status(res) mustBe BAD_REQUEST
      contentAsString(res) must include("Enter your home reference")
    }

    "accept a valid report and show it on the home page" in {
      val req = FakeRequest(POST, "/report")
        .withFormUrlEncodedBody("homeId" -> "h004", "issue" -> "mould", "severity" -> "3", "description" -> "Mould behind wardrobe")
        .withCSRFToken
      val res = route(app, req).get
      status(res) mustBe SEE_OTHER
      redirectLocation(res) mustBe Some("/report/thanks")
      contentAsString(route(app, FakeRequest(GET, "/homes/H004")).get) must include("Mould behind wardrobe")
    }
  }
}
