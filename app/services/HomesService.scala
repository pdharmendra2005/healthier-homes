package services

import models._
import repositories.HomeRepository

import java.time.{Clock, LocalDate}
import javax.inject.{Inject, Singleton}

final case class OutcomeSummary(done: Seq[(HomeAction, Outcome, RiskAssessment)], open: Seq[(HomeAction, RiskAssessment)]) {
  def worked: Int    = done.count(_._2.result.worked)
  def notWorked: Int = done.size - worked
  def workedPct: Int = if (done.isEmpty) 0 else worked * 100 / done.size
}

@Singleton
class HomesService @Inject() (repo: HomeRepository, risk: RiskService, clock: Clock) {

  def today: LocalDate = LocalDate.now(clock)

  def assessById(id: String): Option[RiskAssessment] = repo.homeData(id).map(risk.assess(_, today))

  def ranked: Seq[RiskAssessment] = repo.allIds.flatMap(assessById).sortBy(a => (-a.score, a.property.id))

  /** Highest-risk homes not already being helped, limited to what staff can do this week. */
  def worklist(ranked: Seq[RiskAssessment], capacity: Int): Seq[RiskAssessment] =
    ranked.filter(a => a.band != RiskBand.Low && a.openAction.isEmpty).take(capacity)

  def outcomes: OutcomeSummary = {
    val byId    = ranked.map(a => a.property.id -> a).toMap
    val actions = repo.allActions.flatMap(a => byId.get(a.propertyId).map(a -> _))
    OutcomeSummary(
      done = actions.flatMap { case (a, r) => a.outcome.map(o => (a, o, r)) },
      open = actions.filter(_._1.outcome.isEmpty)
    )
  }
}
