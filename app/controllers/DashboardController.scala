package controllers

import models.RiskBand
import repositories.HomeRepository
import services.HomesService

import javax.inject.{Inject, Singleton}
import play.api.mvc._

@Singleton
class DashboardController @Inject() (cc: MessagesControllerComponents, homes: HomesService, repo: HomeRepository)
    extends MessagesAbstractController(cc) {

  def index(band: Option[String]): Action[AnyContent] = Action { implicit request =>
    val selected = band.flatMap(RiskBand.fromLabel)
    val all      = homes.ranked
    val shown    = selected.fold(all)(b => all.filter(_.band == b))
    Ok(views.html.dashboard(shown, all, selected))
  }

  def home(id: String): Action[AnyContent] = Action { implicit request =>
    homes.assessById(id) match {
      case Some(a) => Ok(views.html.home(a, repo.readingsFor(id), repo.reportsFor(id)))
      case None    => NotFound(views.html.notFound(s"No home with reference $id"))
    }
  }

  def scheduleVisit(id: String): Action[AnyContent] = Action { implicit request =>
    repo.scheduleVisit(id, homes.today.plusDays(2)) match {
      case Some(_) => Redirect(routes.DashboardController.home(id)).flashing("success" -> "Visit booked for 2 days' time")
      case None    => NotFound(views.html.notFound(s"No home with reference $id"))
    }
  }
}
