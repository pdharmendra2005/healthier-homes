package controllers

import models.{Reason, RiskAssessment}
import services.HomesService

import javax.inject.{Inject, Singleton}
import play.api.libs.json._
import play.api.mvc._

/** Machine-readable risk list. Deliberately leaves out names, phone numbers and health details. */
@Singleton
class ApiController @Inject() (cc: ControllerComponents, homes: HomesService) extends AbstractController(cc) {

  private implicit val reasonWrites: Writes[Reason] = (r: Reason) =>
    Json.obj("points" -> r.points, "text" -> r.text, "source" -> r.source)

  private implicit val assessmentWrites: Writes[RiskAssessment] = (a: RiskAssessment) => Json.obj(
    "homeId"   -> a.property.id,
    "address"  -> a.property.address,
    "score"    -> a.score,
    "band"     -> a.band.label,
    "silent"   -> a.silent,
    "nextStep" -> a.nextStep,
    "reasons"  -> a.reasons
  )

  def atRisk(minScore: Int): Action[AnyContent] = Action {
    Ok(Json.toJson(homes.ranked.filter(_.score >= minScore)))
  }

  def risk(id: String): Action[AnyContent] = Action {
    homes.assessById(id).fold[Result](NotFound(Json.obj("error" -> s"No home with reference $id")))(a => Ok(Json.toJson(a)))
  }
}
