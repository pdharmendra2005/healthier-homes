package services

import models._
import repositories.HomeRepository

import java.time.{Clock, LocalDate}
import javax.inject.{Inject, Singleton}

@Singleton
class HomesService @Inject() (repo: HomeRepository, risk: RiskService, clock: Clock) {

  def today: LocalDate = LocalDate.now(clock)

  def assess(home: Home): RiskAssessment =
    risk.assess(home, repo.readingsFor(home.id), repo.reportsFor(home.id), today)

  def ranked: Seq[RiskAssessment] = repo.all.map(assess).sortBy(-_.score)

  def assessById(id: String): Option[RiskAssessment] = repo.find(id).map(assess)
}
