package repositories

import models._

import java.time.{Clock, LocalDate}
import javax.inject.{Inject, Singleton}
import scala.collection.concurrent.TrieMap

/** In-memory store with seeded demo data. Swap for a database later. */
@Singleton
class HomeRepository @Inject() (clock: Clock) {

  private val today = LocalDate.now(clock)

  private val homes = TrieMap.from(Seq(
    Home("H001", "Flat 4, Elm Court, Leeds LS6 1AB", "Amina Khan", "07700 900101", 'F', Occupants(2, 1, 0, respiratoryCondition = true)),
    Home("H002", "12 Birch Road, Leeds LS7 2CD", "George Wilson", "07700 900102", 'E', Occupants(1, 0, 1, respiratoryCondition = false)),
    Home("H003", "Flat 9, Oak House, Leeds LS8 3EF", "Priya Patel", "07700 900103", 'C', Occupants(2, 2, 0, respiratoryCondition = false)),
    Home("H004", "3 Ash Close, Leeds LS9 4GH", "Tom Brown", "07700 900104", 'B', Occupants(2, 0, 0, respiratoryCondition = false)),
    Home("H005", "Flat 1, Willow Walk, Leeds LS10 5IJ", "Maria Silva", "07700 900105", 'D', Occupants(1, 0, 0, respiratoryCondition = false)),
    Home("H006", "27 Cedar Street, Leeds LS11 6KL", "Joseph Okafor", "07700 900106", 'G', Occupants(1, 0, 2, respiratoryCondition = true))
  ).map(h => h.id -> h))

  // (homeId, base temp, base humidity) -> 14 days of synthetic sensor readings
  private val readings = TrieMap.from(Seq(
    ("H001", 15.5, 78.0), ("H002", 16.0, 62.0), ("H003", 19.5, 74.0),
    ("H004", 20.5, 50.0), ("H005", 19.0, 66.0)
    // H006 has no sensor data
  ).map { case (id, t, h) =>
    id -> (0 until 14).map(d => Reading(id, today.minusDays(d.toLong), t + (d % 3) * 0.4, h + (d % 4) * 1.5))
  })

  private val reports = TrieMap[String, Vector[TenantReport]](
    "H001" -> Vector(TenantReport("H001", today.minusDays(10), IssueType.Mould, 3, "Black mould in the baby's bedroom")),
    "H006" -> Vector(TenantReport("H006", today.minusDays(5), IssueType.Cold, 3, "Heating not working, using the oven to keep warm"))
  )

  def all: Seq[Home] = homes.values.toSeq.sortBy(_.id)
  def find(id: String): Option[Home] = homes.get(id)
  def readingsFor(id: String): Seq[Reading] = readings.getOrElse(id, Seq.empty).sortBy(_.date)(Ordering[LocalDate].reverse)
  def reportsFor(id: String): Seq[TenantReport] = reports.getOrElse(id, Vector.empty).sortBy(_.date)(Ordering[LocalDate].reverse)

  def addReport(r: TenantReport): Unit =
    reports.updateWith(r.homeId)(existing => Some(existing.getOrElse(Vector.empty) :+ r))

  def scheduleVisit(id: String, date: LocalDate): Option[Home] =
    homes.updateWith(id)(_.map(_.copy(visitScheduled = Some(date))))
}
