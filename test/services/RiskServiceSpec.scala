package services

import models._
import org.scalatestplus.play.PlaySpec

import java.time.LocalDate

class RiskServiceSpec extends PlaySpec {

  val service = new RiskService
  val today   = LocalDate.of(2026, 10, 22)

  val goodProperty = Property("T1", "1 Test St", Some(WallType.CavityInsulated), Some('B'), Some(HeatingType.GasCentral))
  // EPC F (15) + solid walls (10) + storage heaters (10) = 35
  val poorProperty = Property("T1", "1 Test St", Some(WallType.SolidStone), Some('F'), Some(HeatingType.ElectricStorage))
  val adults = Household("T1", "Test Tenant", "000", 0, 0, sensitiveInfoConsent = false,
    respiratoryCondition = false, lowIncome = false, lastContact = Some(today.minusMonths(1)))

  def data(
    p: Property = goodProperty,
    h: Household = adults,
    repairs: Seq[RepairJob] = Nil,
    readings: Seq[Reading] = Nil,
    reports: Seq[TenantReport] = Nil,
    actions: Seq[HomeAction] = Nil
  ) = HomeData(p, h, repairs, readings, reports, actions)

  def assess(d: HomeData): RiskAssessment = service.assess(d, today)
  def days(temp: Double, humidity: Double) = (0 until 14).map(i => Reading("T1", today.minusDays(i.toLong), temp, humidity))
  def damp(monthsAgo: Int) = RepairJob("T1", today.minusMonths(monthsAgo.toLong), RepairCategory.DampMould, "")

  "Scoring" should {
    "give a warm, well insulated home with no history a score of 0" in {
      val a = assess(data())
      a.score mustBe 0
      a.band mustBe RiskBand.Low
      a.reasons mustBe empty
      a.nextStep mustBe "No action needed now"
    }

    "use building information Cloch already holds - no sensor needed" in {
      val a = assess(data(poorProperty))
      a.score mustBe 35
      a.band mustBe RiskBand.Medium
    }

    "count damp repairs in the last 12 months and suggest a survey" in {
      val a = assess(data(repairs = Seq(damp(2), damp(8))))
      a.score mustBe 25
      a.nextStep must include("damp and mould survey")
    }

    "ignore repairs older than 12 months" in {
      assess(data(repairs = Seq(damp(13)))).score mustBe 0
    }
  }

  "Fairness" should {
    "never flag a home only because of who lives there" in {
      val vulnerable = adults.copy(over65 = 2, childrenUnder5 = 1, sensitiveInfoConsent = true, respiratoryCondition = true, lowIncome = true)
      assess(data(h = vulnerable)).score mustBe 0
    }

    "raise the priority of a risky home when a young child lives there" in {
      assess(data(poorProperty, adults.copy(childrenUnder5 = 1))).score mustBe 44 // 35 x 1.25
    }

    "ignore health and income information without consent" in {
      assess(data(poorProperty, adults.copy(respiratoryCondition = true, lowIncome = true))).score mustBe 35
    }

    "use health information when the tenant has consented" in {
      assess(data(poorProperty, adults.copy(respiratoryCondition = true, sensitiveInfoConsent = true))).score mustBe 40 // 35 x 1.15
    }

    "not give a lower score to a home just because it has no sensor" in {
      assess(data(poorProperty)).score mustBe assess(data(poorProperty, readings = days(20, 55))).score
    }

    "treat unknown property details as 'please check', not as safe" in {
      val unknown = assess(data(Property("T1", "x", None, None, None)))
      unknown.score must be > assess(data()).score
      unknown.reasons.filter(_.isUnknown) must have size 3
      unknown.nextStep must include("missing property details")
    }

    "flag a risky home where the tenant hasn't been in touch for over a year" in {
      val a = assess(data(poorProperty, adults.copy(lastContact = Some(today.minusMonths(14)))))
      a.silent mustBe true
      a.score mustBe 50
      a.nextStep must include("welcome-check call")
    }

    "not treat silence as a risk when the home itself looks fine" in {
      val a = assess(data(h = adults.copy(lastContact = None)))
      a.silent mustBe false
      a.score mustBe 0
    }
  }

  "Explainability" should {
    val worstHousehold = adults.copy(childrenUnder5 = 1, over65 = 1, sensitiveInfoConsent = true, respiratoryCondition = true, lowIncome = true)
    val worst = data(poorProperty, worstHousehold, repairs = Seq(damp(1), damp(2), damp(3)), readings = days(14, 85),
      reports = Seq(TenantReport("T1", today, IssueType.Mould, 3, "")))

    "make the reason points add up exactly to the score" in {
      Seq(data(), data(poorProperty), data(poorProperty, worstHousehold), worst).foreach { d =>
        val a = assess(d)
        a.reasons.map(_.points).sum mustBe a.score
      }
    }

    "cap the score at 100 and say so" in {
      val a = assess(worst)
      a.score mustBe 100
      a.reasons.exists(_.text.contains("capped")) mustBe true
    }

    "give every reason a data source" in {
      all(assess(worst).reasons.map(_.source)) must not be empty
    }
  }

  "Did it work" should {
    "drop repairs from before a resolved outcome" in {
      val fixed = HomeAction(1, "T1", today.minusMonths(4), ActionKind.MouldTreatment, "",
        Some(Outcome(today.minusMonths(3), OutcomeResult.Resolved, "")))
      assess(data(repairs = Seq(damp(5)), actions = Seq(fixed))).score mustBe 0
    }

    "suggest escalating when the last action didn't work" in {
      val failed = HomeAction(1, "T1", today.minusMonths(2), ActionKind.DampSurvey, "",
        Some(Outcome(today.minusMonths(1), OutcomeResult.NoChange, "")))
      assess(data(poorProperty, repairs = Seq(damp(3)), actions = Seq(failed))).nextStep must include("escalate")
    }

    "ask for a follow-up when an action has no outcome yet" in {
      val open = HomeAction(1, "T1", today.minusDays(3), ActionKind.HeatingRepair, "")
      assess(data(poorProperty, actions = Seq(open))).nextStep must startWith("Follow up")
    }
  }
}
