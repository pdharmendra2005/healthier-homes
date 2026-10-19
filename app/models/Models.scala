package models

import java.time.LocalDate

sealed abstract class IssueType(val key: String, val label: String)
object IssueType {
  case object Mould        extends IssueType("mould", "Mould (black spots on walls, ceilings or windows)")
  case object Damp         extends IssueType("damp", "Damp (wet patches, peeling paint, musty smell)")
  case object Condensation extends IssueType("condensation", "Condensation (water running down windows)")
  case object Cold         extends IssueType("cold", "Cold (can't keep the home warm)")
  case object Leak         extends IssueType("leak", "Leak (water coming in)")

  val all: Seq[IssueType] = Seq(Mould, Damp, Condensation, Cold, Leak)
  def fromKey(key: String): Option[IssueType] = all.find(_.key == key)
}

final case class Occupants(
  adults: Int,
  childrenUnder5: Int,
  over65: Int,
  respiratoryCondition: Boolean
) {
  def isVulnerable: Boolean = childrenUnder5 > 0 || over65 > 0 || respiratoryCondition
}

final case class Home(
  id: String,
  address: String,
  tenantName: String,
  phone: String,
  epcRating: Char,
  occupants: Occupants,
  visitScheduled: Option[LocalDate] = None
)

/** One day's average from an indoor sensor (or a manual reading at a visit). */
final case class Reading(homeId: String, date: LocalDate, temperatureC: Double, humidityPct: Double)

final case class TenantReport(
  homeId: String,
  date: LocalDate,
  issue: IssueType,
  severity: Int,
  description: String
)

sealed abstract class RiskBand(val label: String, val cssClass: String)
object RiskBand {
  case object High   extends RiskBand("High", "high")
  case object Medium extends RiskBand("Medium", "medium")
  case object Low    extends RiskBand("Low", "low")

  def fromScore(score: Int): RiskBand =
    if (score >= 60) High else if (score >= 30) Medium else Low

  def fromLabel(s: String): Option[RiskBand] =
    Seq(High, Medium, Low).find(_.label.equalsIgnoreCase(s))
}

final case class RiskAssessment(home: Home, score: Int, band: RiskBand, reasons: Seq[String])
