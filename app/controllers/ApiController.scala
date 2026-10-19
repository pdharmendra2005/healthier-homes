package controllers

import models.RiskAssessment
import services.HomesService

import javax.inject.{Inject, Singleton}
import play.api.libs.json._
import play.api.mvc._

@Singleton
class ApiController @Inject() (cc: ControllerComponents, homes: HomesService) extends AbstractController(cc) {

  private implicit val assessmentWrites: Writes[RiskAssessment] = (a: RiskAssessment) => Json.obj(
    "homeId"         -> a.home.id,
    "address"        -> a.home.address,
    "score"          -> a.score,
    "band"           -> a.band.label,
    "reasons"        -> a.reasons,
    "visitScheduled" -> a.home.visitScheduled.map(_.toString)
  )

  def atRisk(minScore: Int): Action[AnyContent] = Action {
    Ok(Json.toJson(homes.ranked.filter(_.score >= minScore)))
  }

  def risk(id: String): Action[AnyContent] = Action {
    homes.assessById(id).fold[Result](NotFound(Json.obj("error" -> s"No home with reference $id")))(a => Ok(Json.toJson(a)))
  }
}
