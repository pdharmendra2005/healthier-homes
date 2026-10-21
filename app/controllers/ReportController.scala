package controllers

import models.{IssueType, TenantReport}
import repositories.HomeRepository
import services.HomesService

import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms._
import play.api.mvc._

final case class ReportData(homeId: String, issue: String, severity: Int, description: String, breathingConsent: Boolean)

@Singleton
class ReportController @Inject() (cc: MessagesControllerComponents, repo: HomeRepository, homes: HomesService)
    extends MessagesAbstractController(cc) {

  val reportForm: Form[ReportData] = Form(
    mapping(
      "homeId" -> text
        .transform[String](_.trim.toUpperCase, identity)
        .verifying("Enter your home reference, for example P01", repo.exists(_)),
      "issue"            -> default(text, "").verifying("Choose what the problem is", IssueType.fromKey(_).isDefined),
      "severity"         -> number(min = 1, max = 3),
      "description"      -> text(maxLength = 500),
      "breathingConsent" -> boolean
    )(ReportData.apply)(ReportData.unapply)
  )

  def form: Action[AnyContent] = Action { implicit request =>
    Ok(views.html.report(reportForm))
  }

  def submit: Action[AnyContent] = Action { implicit request =>
    reportForm.bindFromRequest().fold(
      errors => BadRequest(views.html.report(errors)),
      data => {
        val report = TenantReport(data.homeId, homes.today, IssueType.fromKey(data.issue).get, data.severity, data.description.trim)
        repo.addReport(report, data.breathingConsent)
        Redirect(routes.ReportController.thanks)
      }
    )
  }

  def thanks: Action[AnyContent] = Action { implicit request =>
    Ok(views.html.thanks())
  }
}
