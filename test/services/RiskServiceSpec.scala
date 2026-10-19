package services

import models._
import org.scalatestplus.play.PlaySpec

import java.time.LocalDate

class RiskServiceSpec extends PlaySpec {

  val service = new RiskService
  val today   = LocalDate.of(2026, 10, 22)

  def home(epc: Char = 'C', occupants: Occupants = Occupants(2, 0, 0, respiratoryCondition = false)) =
    Home("T1", "1 Test St", "Test Tenant", "000", epc, occupants)

  def days(temp: Double, humidity: Double, n: Int = 14) =
    (0 until n).map(d => Reading("T1", today.minusDays(d.toLong), temp, humidity))

  "RiskService.assess" should {

    "score a warm, dry, well insulated home as Low with no reasons" in {
      val a = service.assess(home(), days(21, 50), Nil, today)
      a.score mustBe 0
      a.band mustBe RiskBand.Low
      a.reasons mustBe empty
    }

    "flag a cold and damp home as Medium with both reasons" in {
      val a = service.assess(home(), days(15, 80), Nil, today)
      a.score mustBe 50
      a.band mustBe RiskBand.Medium
      a.reasons.exists(_.startsWith("Cold")) mustBe true
      a.reasons.exists(_.startsWith("Damp")) mustBe true
    }

    "raise the score for vulnerable occupants and say why" in {
      val vulnerable = Occupants(1, 1, 0, respiratoryCondition = false)
      val a = service.assess(home(occupants = vulnerable), days(15, 80), Nil, today)
      a.score mustBe 65
      a.band mustBe RiskBand.High
      a.reasons must contain("Vulnerable: 1 child(ren) under 5")
    }

    "not flag vulnerability alone when there is no environmental risk" in {
      val a = service.assess(home(occupants = Occupants(1, 0, 2, respiratoryCondition = true)), days(21, 50), Nil, today)
      a.score mustBe 0
    }

    "count a recent mould report but ignore one older than 90 days" in {
      val recent = TenantReport("T1", today.minusDays(5), IssueType.Mould, 2, "")
      val old    = recent.copy(date = today.minusDays(120))
      service.assess(home(), days(21, 50), Seq(recent), today).score mustBe 25
      service.assess(home(), days(21, 50), Seq(old), today).score mustBe 0
    }

    "cap the score at 100" in {
      val worst = Occupants(1, 2, 1, respiratoryCondition = true)
      val a = service.assess(home('G', worst), days(12, 90), Seq(TenantReport("T1", today, IssueType.Mould, 3, "")), today)
      a.score mustBe 100
    }

    "treat exactly 18C and 70% humidity as OK (boundaries)" in {
      service.assess(home(), days(18.0, 70.0), Nil, today).score mustBe 0
    }

    "warn when there is no sensor data" in {
      val a = service.assess(home(), Nil, Nil, today)
      a.reasons.exists(_.contains("No sensor data")) mustBe true
    }
  }
}
