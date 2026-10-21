package models

import java.time.LocalDate

sealed abstract class IssueType(val key: String, val label: String)
object IssueType {
  case object Mould        extends IssueType("mould", "Mould (black spots on walls, ceilings or windows)")
  case object Damp         extends IssueType("damp", "Damp (wet patches, peeling paint, musty smell)")
  case object Condensation extends IssueType("condensation", "Condensation (water running down windows)")
  case object Cold         extends IssueType("cold", "Cold (can't keep the home warm)")
  case object Leak         extends IssueType("leak", "Leak (water coming in)")

  val all: Seq[IssueType]          = Seq(Mould, Damp, Condensation, Cold, Leak)
  val dampRelated: Set[IssueType]  = Set(Mould, Damp, Condensation)
  def fromKey(key: String): Option[IssueType] = all.find(_.key == key)
}

sealed abstract class WallType(val label: String, val hardToHeat: Boolean)
object WallType {
  case object SolidStone      extends WallType("Solid stone (tenement)", hardToHeat = true)
  case object NonTraditional  extends WallType("Non-traditional concrete", hardToHeat = true)
  case object CavityInsulated extends WallType("Cavity wall, insulated", hardToHeat = false)
}

sealed abstract class HeatingType(val label: String, val costlyToRun: Boolean)
object HeatingType {
  case object GasCentral      extends HeatingType("Gas central heating", costlyToRun = false)
  case object ElectricStorage extends HeatingType("Electric storage heaters", costlyToRun = true)
}

/** What Cloch already holds about the building (property records). None = not known. */
final case class Property(
  id: String,
  address: String,
  wallType: Option[WallType],
  epcRating: Option[Char],
  heating: Option[HeatingType]
)

/** Who lives there (tenancy records). Health and income are only used with consent. */
final case class Household(
  propertyId: String,
  tenantName: String,
  phone: String,
  childrenUnder5: Int,
  over65: Int,
  sensitiveInfoConsent: Boolean,
  respiratoryCondition: Boolean,
  lowIncome: Boolean,
  lastContact: Option[LocalDate]
)

sealed abstract class RepairCategory(val label: String)
object RepairCategory {
  case object DampMould extends RepairCategory("Damp / mould")
  case object Heating   extends RepairCategory("Heating")
  case object Leak      extends RepairCategory("Leak")
  case object Other     extends RepairCategory("Other")
}

final case class RepairJob(propertyId: String, date: LocalDate, category: RepairCategory, description: String)

/** Daily average from a pilot sensor. Most homes don't have one. */
final case class Reading(propertyId: String, date: LocalDate, temperatureC: Double, humidityPct: Double)

final case class TenantReport(propertyId: String, date: LocalDate, issue: IssueType, severity: Int, description: String)

sealed abstract class ActionKind(val key: String, val label: String)
object ActionKind {
  case object WelcomeCall    extends ActionKind("welcome-call", "Welcome-check call")
  case object DampSurvey     extends ActionKind("damp-survey", "Damp and mould survey")
  case object MouldTreatment extends ActionKind("mould-treatment", "Mould wash and treatment")
  case object VentilationFix extends ActionKind("ventilation", "Ventilation fix (extractor fan)")
  case object HeatingRepair  extends ActionKind("heating-repair", "Heating repair")
  case object InsulationWork extends ActionKind("insulation", "Insulation work")
  case object EnergyAdvice   extends ActionKind("energy-advice", "Energy advice")

  val all: Seq[ActionKind] =
    Seq(WelcomeCall, DampSurvey, MouldTreatment, VentilationFix, HeatingRepair, InsulationWork, EnergyAdvice)
  def fromKey(key: String): Option[ActionKind] = all.find(_.key == key)
}

sealed abstract class OutcomeResult(val key: String, val label: String, val worked: Boolean)
object OutcomeResult {
  case object Resolved extends OutcomeResult("resolved", "Resolved", worked = true)
  case object Improved extends OutcomeResult("improved", "Improved", worked = true)
  case object NoChange extends OutcomeResult("no-change", "No change", worked = false)
  case object Worse    extends OutcomeResult("worse", "Worse", worked = false)

  val all: Seq[OutcomeResult] = Seq(Resolved, Improved, NoChange, Worse)
  def fromKey(key: String): Option[OutcomeResult] = all.find(_.key == key)
}

final case class Outcome(date: LocalDate, result: OutcomeResult, notes: String)

final case class HomeAction(
  id: Int,
  propertyId: String,
  date: LocalDate,
  kind: ActionKind,
  notes: String,
  outcome: Option[Outcome] = None
)

/** Everything we know about one home, joined up from the separate systems. */
final case class HomeData(
  property: Property,
  household: Household,
  repairs: Seq[RepairJob],
  readings: Seq[Reading],
  reports: Seq[TenantReport],
  actions: Seq[HomeAction]
)

sealed abstract class RiskBand(val label: String, val cssClass: String)
object RiskBand {
  case object High   extends RiskBand("High", "high")
  case object Medium extends RiskBand("Medium", "medium")
  case object Low    extends RiskBand("Low", "low")

  val all: Seq[RiskBand] = Seq(High, Medium, Low)
  def fromScore(score: Int): RiskBand = if (score >= 50) High else if (score >= 25) Medium else Low
  def fromLabel(s: String): Option[RiskBand] = all.find(_.label.equalsIgnoreCase(s))
}

/** One line of the explanation. The points of all reasons add up to the score. */
final case class Reason(points: Int, text: String, source: String) {
  def isUnknown: Boolean = source == Reason.Unknown
}
object Reason {
  val Unknown = "Not known - please check"
}

final case class RiskAssessment(
  data: HomeData,
  score: Int,
  band: RiskBand,
  reasons: Seq[Reason],
  nextStep: String,
  silent: Boolean
) {
  def property: Property            = data.property
  def household: Household          = data.household
  def openAction: Option[HomeAction] = data.actions.find(_.outcome.isEmpty)
  def mainReason: String =
    reasons.filter(_.points > 0).sortBy(-_.points).headOption.map(_.text).getOrElse("No concerns")
}
