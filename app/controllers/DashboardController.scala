package controllers

import models._
import repositories.HomeRepository
import services.HomesService

import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms._
import play.api.mvc._

@Singleton
class DashboardController @Inject() (cc: MessagesControllerComponents, homes: HomesService, repo: HomeRepository)
    extends MessagesAbstractController(cc) {

  private val actionForm = Form(tuple(
    "kind"  -> default(text, "").verifying("Choose an action", ActionKind.fromKey(_).isDefined),
    "notes" -> text(maxLength = 500)
  ))

  private val outcomeForm = Form(tuple(
    "result" -> default(text, "").verifying("Choose whether it worked", OutcomeResult.fromKey(_).isDefined),
    "notes"  -> text(maxLength = 500)
  ))

  def index(band: Option[String], capacity: Int): Action[AnyContent] = Action { implicit request =>
    val cap      = math.max(1, math.min(50, capacity))
    val selected = band.flatMap(RiskBand.fromLabel)
    val all      = homes.ranked
    val shown    = selected.fold(all)(b => all.filter(_.band == b))
    Ok(views.html.dashboard(homes.worklist(all, cap), all, shown, selected, cap))
  }

  def home(id: String): Action[AnyContent] = Action { implicit request =>
    homes.assessById(id).fold(notFound(id))(a => Ok(views.html.home(a)))
  }

  def addAction(id: String): Action[AnyContent] = Action { implicit request =>
    actionForm.bindFromRequest().fold(
      errors => backToHome(id).flashing("error" -> firstError(errors)),
      { case (kind, notes) =>
        repo.addAction(id, homes.today, ActionKind.fromKey(kind).get, notes.trim).fold(notFound(id)) { a =>
          backToHome(id).flashing("success" -> s"${a.kind.label} recorded. Add the outcome when you know if it worked.")
        }
      }
    )
  }

  def recordOutcome(id: String, actionId: Int): Action[AnyContent] = Action { implicit request =>
    outcomeForm.bindFromRequest().fold(
      errors => backToHome(id).flashing("error" -> firstError(errors)),
      { case (result, notes) =>
        repo.homeData(id).flatMap(_.actions.find(_.id == actionId)) match {
          case Some(_) =>
            repo.recordOutcome(actionId, Outcome(homes.today, OutcomeResult.fromKey(result).get, notes.trim))
            backToHome(id).flashing("success" -> "Outcome saved")
          case None => notFound(s"$id action $actionId")
        }
      }
    )
  }

  def outcomes: Action[AnyContent] = Action { implicit request =>
    Ok(views.html.outcomes(homes.outcomes))
  }

  private def backToHome(id: String) = Redirect(routes.DashboardController.home(id))

  private def notFound(id: String)(implicit request: MessagesRequestHeader) =
    NotFound(views.html.notFound(s"No home with reference $id"))

  private def firstError(form: Form[_])(implicit request: MessagesRequestHeader): String =
    form.errors.headOption.map(e => request.messages(e.message, e.args: _*)).getOrElse("Something went wrong")
}
