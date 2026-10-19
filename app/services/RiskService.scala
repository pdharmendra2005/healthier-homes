package services

import models._

import java.time.LocalDate
import javax.inject.Singleton

/**
 * Rule-based risk score (0-100). Deliberately simple and explainable so an officer
 * can see *why* a home is flagged. Thresholds:
 *  - below 18C indoors is a health risk (WHO / UK Health Security Agency guidance)
 *  - relative humidity above 70% for long periods lets mould grow
 */
@Singleton
class RiskService {

  val ColdThresholdC     = 18.0
  val HumidityThreshold  = 70.0
  val LookbackDays       = 14

  def assess(home: Home, readings: Seq[Reading], reports: Seq[TenantReport], today: LocalDate): RiskAssessment = {
    val recent = readings.filter(r => !r.date.isBefore(today.minusDays(LookbackDays.toLong)))
    val recentReports = reports.filter(r => !r.date.isBefore(today.minusDays(90)))

    val coldDays  = recent.count(_.temperatureC < ColdThresholdC)
    val dampDays  = recent.count(_.humidityPct > HumidityThreshold)

    val parts: Seq[(Int, Option[String])] = Seq(
      if (recent.nonEmpty && coldDays * 2 >= recent.size)
        25 -> Some(f"Cold: below ${ColdThresholdC}%.0f°C on $coldDays of ${recent.size} recent days (avg ${avg(recent.map(_.temperatureC))}%.1f°C)")
      else if (coldDays > 0) 10 -> Some(s"Cold on $coldDays of ${recent.size} recent days")
      else 0 -> None,
      if (recent.nonEmpty && dampDays * 2 >= recent.size)
        25 -> Some(f"Damp: humidity above ${HumidityThreshold}%.0f%% on $dampDays of ${recent.size} recent days (avg ${avg(recent.map(_.humidityPct))}%.0f%%)")
      else if (dampDays > 0) 10 -> Some(s"Humid on $dampDays of ${recent.size} recent days")
      else 0 -> None,
      if (recentReports.exists(_.issue == IssueType.Mould)) 25 -> Some("Tenant reported mould in the last 90 days")
      else if (recentReports.nonEmpty) 10 -> Some(s"Tenant reported: ${recentReports.map(_.issue.key).distinct.mkString(", ")}")
      else 0 -> None,
      home.epcRating match {
        case 'E' | 'F' | 'G' => 10 -> Some(s"Poor insulation (EPC ${home.epcRating})")
        case _               => 0 -> None
      },
      if (recent.isEmpty) 5 -> Some("No sensor data in the last 14 days - check the sensor") else 0 -> None
    )

    val base = parts.map(_._1).sum
    val vulnerable = home.occupants.isVulnerable
    val score = math.min(100, if (vulnerable && base > 0) (base * 1.3).round.toInt else base)
    val reasons = parts.flatMap(_._2) ++ vulnerabilityReasons(home.occupants).filter(_ => base > 0)

    RiskAssessment(home, score, RiskBand.fromScore(score), reasons)
  }

  private def vulnerabilityReasons(o: Occupants): Seq[String] = Seq(
    Option.when(o.childrenUnder5 > 0)(s"Vulnerable: ${o.childrenUnder5} child(ren) under 5"),
    Option.when(o.over65 > 0)(s"Vulnerable: ${o.over65} resident(s) over 65"),
    Option.when(o.respiratoryCondition)("Vulnerable: resident with a respiratory condition")
  ).flatten

  private def avg(xs: Seq[Double]): Double = if (xs.isEmpty) 0.0 else xs.sum / xs.size
}
