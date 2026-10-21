package services

import models._

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Singleton

/**
 * Explainable, rule-based score (0-100). Every point comes from a Reason, so staff,
 * tenants and auditors can see exactly why a home is on the list.
 *
 * Fairness rules:
 *  - Sensors are optional: not having one never lowers a score.
 *  - Unknown property details add a few points ("please check") instead of counting as safe.
 *  - Silence (no contact for 12+ months) only counts when the building itself looks risky.
 *  - Who lives there only raises an existing home risk; it never flags a home on its own.
 *  - Health and income information is only used with the tenant's consent.
 */
@Singleton
class RiskService {

  val ColdThresholdC    = 18.0
  val HumidityThreshold = 70.0
  val SilentAfterMonths = 12L

  def assess(d: HomeData, today: LocalDate): RiskAssessment = {
    // Anything from before a "Resolved" outcome has been dealt with.
    val lastFixed = d.actions.flatMap(_.outcome).filter(_.result == OutcomeResult.Resolved).map(_.date).maxOption
    def sinceFix(date: LocalDate) = lastFixed.forall(date.isAfter)

    val repairs  = d.repairs.filter(r => r.date.isAfter(today.minusMonths(12)) && sinceFix(r.date))
    val reports  = d.reports.filter(r => r.date.isAfter(today.minusDays(90)) && sinceFix(r.date))
    val readings = d.readings.filter(r => r.date.isAfter(today.minusDays(15)))

    val dampRepairs   = repairs.count(_.category == RepairCategory.DampMould)
    val heatingRepair = repairs.exists(_.category == RepairCategory.Heating)
    val coldSensor    = readings.nonEmpty && readings.count(_.temperatureC < ColdThresholdC) * 2 >= readings.size
    val humidSensor   = readings.nonEmpty && readings.count(_.humidityPct > HumidityThreshold) * 2 >= readings.size

    val homeReasons: Seq[Reason] = Seq(
      Option.when(dampRepairs > 0)(
        Reason(math.min(30, 15 + 5 * dampRepairs), s"$dampRepairs damp or mould repair(s) in the last 12 months", "Repairs system")),
      Option.when(heatingRepair)(Reason(10, "Heating repair in the last 12 months", "Repairs system")),
      Option.when(repairs.exists(_.category == RepairCategory.Leak))(Reason(10, "Leak repair in the last 12 months", "Repairs system")),
      d.property.epcRating match {
        case Some(c) if "EFG".contains(c) => Some(Reason(15, s"Poor energy rating (EPC $c)", "Property records"))
        case Some('D')                    => Some(Reason(5, "Below-average energy rating (EPC D)", "Property records"))
        case Some(_)                      => None
        case None                         => Some(Reason(8, "Energy rating not known", Reason.Unknown))
      },
      d.property.wallType match {
        case Some(w) if w.hardToHeat => Some(Reason(10, s"Hard-to-heat walls: ${w.label}", "Property records"))
        case Some(_)                 => None
        case None                    => Some(Reason(5, "Wall type not known", Reason.Unknown))
      },
      d.property.heating match {
        case Some(h) if h.costlyToRun => Some(Reason(10, s"Costly to heat: ${h.label}", "Property records"))
        case Some(_)                  => None
        case None                     => Some(Reason(5, "Heating type not known", Reason.Unknown))
      },
      if (reports.exists(_.issue == IssueType.Mould)) Some(Reason(20, "Tenant reported mould in the last 90 days", "Tenant reports"))
      else Option.when(reports.nonEmpty)(
        Reason(10, s"Tenant reported: ${reports.map(_.issue.key).distinct.mkString(", ")}", "Tenant reports")),
      Option.when(coldSensor)(
        Reason(15, f"Below $ColdThresholdC%.0f°C on at least half of the last 14 days", "Pilot sensor")),
      Option.when(humidSensor)(
        Reason(15, f"Humidity above $HumidityThreshold%.0f%% on at least half of the last 14 days", "Pilot sensor"))
    ).flatten

    val homeScore          = homeReasons.map(_.points).sum
    val monthsSinceContact = d.household.lastContact.map(ChronoUnit.MONTHS.between(_, today))
    val silent             = homeScore >= 20 && monthsSinceContact.forall(_ >= SilentAfterMonths)
    val silence = Option.when(silent)(Reason(15,
      monthsSinceContact.fold("No contact on record")(m => s"No contact for $m months - problems may not be reported"),
      "Housing management system"))

    val base = homeScore + silence.map(_.points).sum

    val h = d.household
    val ageGroups = Seq(
      Option.when(h.childrenUnder5 > 0)("young child"),
      Option.when(h.over65 > 0)("resident over 65")
    ).flatten
    val factors: Seq[(Double, String)] = Seq(
      Option.when(ageGroups.nonEmpty)(0.25 -> ageGroups.mkString(" and ")),
      Option.when(h.sensitiveInfoConsent && h.respiratoryCondition)(0.15 -> "breathing condition (shared with consent)"),
      Option.when(h.sensitiveInfoConsent && h.lowIncome)(0.10 -> "low income (shared with consent)")
    ).flatten
    val factor   = 1.0 + factors.map(_._1).sum
    val uncapped = math.round(base * factor).toInt
    val household = Option.when(base > 0 && factors.nonEmpty)(Reason(uncapped - base,
      f"Household more at risk to health: ${factors.map(_._2).mkString(", ")} (x$factor%.2f)", "Tenancy records"))

    val score   = math.min(100, uncapped)
    val cap     = Option.when(uncapped > 100)(Reason(100 - uncapped, "Score capped at 100", "Scoring rule"))
    val reasons = homeReasons ++ silence ++ household ++ cap

    val open        = d.actions.filter(_.outcome.isEmpty).sortBy(_.date).lastOption
    val lastOutcome = d.actions.flatMap(a => a.outcome.map(a -> _)).sortBy(_._2.date).lastOption
    val dampSigns   = dampRepairs > 0 || humidSensor || reports.exists(r => IssueType.dampRelated(r.issue))
    val coldSigns   = heatingRepair || coldSensor || reports.exists(_.issue == IssueType.Cold)

    val nextStep = (open, lastOutcome) match {
      case (Some(a), _) =>
        s"Follow up: ${a.kind.label} on ${a.date} has no outcome recorded yet"
      case (None, Some((a, o))) if !o.result.worked && score >= 25 =>
        s"Last action (${a.kind.label}) didn't work - escalate to a surveyor for repair or insulation work"
      case _ if dampSigns                   => "Book a damp and mould survey within 10 working days"
      case _ if coldSigns                   => "Book a heating check and offer energy advice"
      case _ if silent                      => "Make a friendly welcome-check call and offer a home visit"
      case _ if reasons.exists(_.isUnknown) => "Collect the missing property details at the next visit"
      case _ if score >= 25                 => "Offer energy advice and add to the insulation programme"
      case _                                => "No action needed now"
    }

    RiskAssessment(d, score, RiskBand.fromScore(score), reasons, nextStep, silent)
  }
}
