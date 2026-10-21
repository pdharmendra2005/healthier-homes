package repositories

import models._

import java.time.{Clock, LocalDate}
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.{Inject, Singleton}
import scala.collection.concurrent.TrieMap

/**
 * In-memory store with fictional demo data, joined up from what would be separate
 * systems at Cloch: property records, tenancy records, repairs, tenant reports,
 * a small sensor pilot and the actions staff take. Swap for a database later.
 */
@Singleton
class HomeRepository @Inject() (clock: Clock) {

  import HeatingType._, RepairCategory._, WallType._

  private val today                   = LocalDate.now(clock)
  private def daysAgo(n: Int)         = today.minusDays(n.toLong)
  private def monthsAgo(n: Int)       = today.minusMonths(n.toLong)
  private val newestFirst             = Ordering[LocalDate].reverse

  private val properties = TrieMap.from(Seq(
    Property("P01", "2/1, 14 Bank Street, Greenock PA15 1AA", Some(SolidStone), Some('E'), Some(GasCentral)),
    Property("P02", "7 Kelburn Crescent, Port Glasgow PA14 5BB", Some(NonTraditional), Some('F'), Some(ElectricStorage)),
    Property("P03", "22 Shore Road, Gourock PA19 1CC", Some(CavityInsulated), Some('C'), Some(GasCentral)),
    Property("P04", "5 Lyle Road, Greenock PA16 7DD", Some(CavityInsulated), Some('B'), Some(GasCentral)),
    Property("P05", "1/2, 3 Ann Street, Greenock PA15 2EE", None, None, None),
    Property("P06", "0/1, 41 Dempster Street, Greenock PA15 3FF", Some(SolidStone), Some('G'), Some(GasCentral)),
    Property("P07", "9 Cardwell Road, Gourock PA19 1GG", Some(CavityInsulated), Some('B'), Some(GasCentral)),
    Property("P08", "16 Princes Street, Port Glasgow PA14 5HH", Some(CavityInsulated), Some('D'), Some(GasCentral)),
    Property("P09", "3/2, 8 Brougham Street, Greenock PA16 8JJ", Some(SolidStone), Some('E'), Some(GasCentral)),
    Property("P10", "12 Bardrainney Avenue, Port Glasgow PA14 6KK", Some(NonTraditional), Some('F'), Some(ElectricStorage))
  ).map(p => p.id -> p))

  // id, name, phone, children under 5, over 65, consent, breathing condition, low income, last contact
  private val households = TrieMap.from(Seq(
    Household("P01", "Amina Khan", "07700 900101", 1, 0, true, true, true, Some(daysAgo(10))),
    Household("P02", "Margaret Doyle", "07700 900102", 0, 1, false, false, true, Some(monthsAgo(20))),
    Household("P03", "Priya Patel", "07700 900103", 2, 0, false, false, false, Some(monthsAgo(3))),
    Household("P04", "Tom Brown", "07700 900104", 0, 0, false, false, false, Some(monthsAgo(2))),
    Household("P05", "Callum Reid", "07700 900105", 0, 0, false, false, false, Some(monthsAgo(6))),
    Household("P06", "Joseph Okafor", "07700 900106", 0, 2, true, true, false, Some(daysAgo(5))),
    Household("P07", "Isobel Grant", "07700 900107", 0, 1, true, true, false, Some(monthsAgo(4))),
    Household("P08", "Fiona Campbell", "07700 900108", 1, 0, false, false, false, Some(monthsAgo(4))),
    Household("P09", "Sean Murphy", "07700 900109", 1, 0, false, false, false, Some(daysAgo(14))),
    Household("P10", "Agnes Wilson", "07700 900110", 0, 1, false, false, false, Some(daysAgo(10)))
  ).map(h => h.propertyId -> h))

  private val repairs = TrieMap[String, Vector[RepairJob]](
    "P01" -> Vector(
      RepairJob("P01", monthsAgo(8), DampMould, "Mould wash in bathroom"),
      RepairJob("P01", monthsAgo(2), DampMould, "Mould back in child's bedroom")),
    "P03" -> Vector(RepairJob("P03", monthsAgo(3), Leak, "Roof leak over kitchen")),
    "P06" -> Vector(RepairJob("P06", monthsAgo(1), Heating, "Boiler not firing")),
    "P08" -> Vector(RepairJob("P08", monthsAgo(5), DampMould, "Black mould in bedroom")),
    "P09" -> Vector(RepairJob("P09", monthsAgo(3), DampMould, "Damp patch on gable wall")),
    "P10" -> Vector(RepairJob("P10", daysAgo(10), Heating, "Storage heater not holding charge"))
  )

  // Sensor pilot: only 3 homes. (id, base temperature, base humidity) -> 14 days of readings
  private val readings = TrieMap.from(Seq(("P01", 15.5, 78.0), ("P03", 19.5, 74.0), ("P04", 20.5, 50.0)).map {
    case (id, t, h) =>
      id -> (0 until 14).map(d => Reading(id, daysAgo(d), t + (d % 3) * 0.4, h + (d % 4) * 1.5))
  })

  private val reports = TrieMap[String, Vector[TenantReport]](
    "P01" -> Vector(TenantReport("P01", daysAgo(10), IssueType.Mould, 3, "Black mould in the baby's bedroom")),
    "P06" -> Vector(TenantReport("P06", daysAgo(5), IssueType.Cold, 3, "Heating not working, using the oven to keep warm")),
    "P09" -> Vector(TenantReport("P09", daysAgo(14), IssueType.Mould, 2, "Mould is back after the survey"))
  )

  private val actionIds = new AtomicInteger(3)
  private val actions = TrieMap[Int, HomeAction](
    1 -> HomeAction(1, "P08", monthsAgo(5).plusDays(7), ActionKind.MouldTreatment, "Mould wash, anti-mould paint, new extractor fan",
      Some(Outcome(monthsAgo(4), OutcomeResult.Resolved, "No mould at 4-week check"))),
    2 -> HomeAction(2, "P09", monthsAgo(2), ActionKind.DampSurvey, "Surveyor advised better ventilation",
      Some(Outcome(daysAgo(45), OutcomeResult.NoChange, "Tenant says damp patch still there"))),
    3 -> HomeAction(3, "P10", daysAgo(3), ActionKind.HeatingRepair, "Engineer booked to replace heater")
  )

  def allIds: Seq[String]        = properties.keys.toSeq.sorted
  def exists(id: String): Boolean = properties.contains(id)
  def allActions: Seq[HomeAction] = actions.values.toSeq.sortBy(_.date)(newestFirst)

  /** Join everything we hold about one home. */
  def homeData(id: String): Option[HomeData] =
    for {
      p <- properties.get(id)
      h <- households.get(id)
    } yield HomeData(
      p,
      h,
      repairs.getOrElse(id, Vector.empty).sortBy(_.date)(newestFirst),
      readings.getOrElse(id, Seq.empty).sortBy(_.date)(newestFirst),
      reports.getOrElse(id, Vector.empty).sortBy(_.date)(newestFirst),
      actions.values.filter(_.propertyId == id).toSeq.sortBy(_.date)(newestFirst)
    )

  def addReport(r: TenantReport, breathingConditionWithConsent: Boolean): Unit = {
    reports.updateWith(r.propertyId)(existing => Some(existing.getOrElse(Vector.empty) :+ r))
    households.updateWith(r.propertyId)(_.map { h =>
      val contacted = h.copy(lastContact = Some(r.date))
      if (breathingConditionWithConsent) contacted.copy(respiratoryCondition = true, sensitiveInfoConsent = true)
      else contacted
    })
  }

  def addAction(propertyId: String, date: LocalDate, kind: ActionKind, notes: String): Option[HomeAction] =
    Option.when(exists(propertyId)) {
      val a = HomeAction(actionIds.incrementAndGet(), propertyId, date, kind, notes)
      actions.put(a.id, a)
      a
    }

  def recordOutcome(actionId: Int, outcome: Outcome): Option[HomeAction] =
    actions.updateWith(actionId)(_.map(_.copy(outcome = Some(outcome))))
}
