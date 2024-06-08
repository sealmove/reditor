package controllers

import models.EditorModel
import java.util.UUID
import javax.inject._
import play.api.Configuration
import play.api.libs.json.JsValue
import play.api.mvc._

@Singleton
class EditorController @Inject()(val controllerComponents: ControllerComponents,
                                 config: Configuration,
                                 model: EditorModel) extends BaseController {
  private val cap = config.get[Int]("ALLOW_LOCK_ACQUISITION_TIMEOUT")
  private val saveAfter = config.get[Int]("SAVE_AFTER")

  def openIndex(): Action[AnyContent] = Action { implicit request =>
    val uuid = request.session.get("uuid") match {
      case Some(uuid) => UUID.fromString(uuid)
      case None => UUID.randomUUID
    }
    Ok(views.html.index(model.getFileListing(uuid))).withSession("uuid" -> uuid.toString)
  }

  def openFile(filename: String): Action[AnyContent] = Action { implicit request =>
    val entry = model.readEntry(filename)
    entry match {
      case Some(e) =>
        val uuid = request.session.get("uuid") match {
          case Some(uuid) => UUID.fromString(uuid)
          case None => UUID.randomUUID
        }
        model.calcState(e, uuid) match {
          case models.Owned =>
            Ok(views.html.editor(e, cap, saveAfter)).withSession("uuid" -> uuid.toString)
          case models.Free =>
            val updated = model.updateUUID(e.id, uuid)
            updated match {
              case Some(u) => Ok(views.html.editor(u, cap, saveAfter)).withSession("uuid" -> uuid.toString)
              case None => InternalServerError // For an unknown reason the UUID could not be updated
            }
          case models.Locked =>
            Locked("The file is being edited by someone else")
          }
      case None => NotFound("File not found")
    }
  }

  def saveFile: Action[JsValue] = Action(parse.json) { implicit request =>
    val maybeUUID = request.session.get("uuid")
    val name = (request.body \ "name").as[String]
    val content = (request.body \ "content").as[String]
    val entry = model.readEntry(name)
    if (maybeUUID.isEmpty) {
      Unauthorized
    } else {
      val uuid = UUID.fromString(maybeUUID.get)
      if (entry.get.lock.isEmpty || entry.get.lock.get != uuid) {
        Forbidden("You have most likely been locked out because of inactivity")
      } else {
        val success = model.updateContent(name, content)
        if (success) {
          val updated = model.readEntry(name)
          updated match {
            case Some(u) => Ok(views.html.editor(u, cap, saveAfter)).withSession("uuid" -> uuid.toString)
            case None => InternalServerError // For an unknown reason the file couldn't be retrieved
          }
        } else {
          InternalServerError // For an unknown reason the file could not be saved
        }
      }
    }
  }

  def createFile: Action[JsValue] = Action(parse.json) { implicit request =>
    val uuid = request.session.get("uuid") match {
      case Some(uuid) => UUID.fromString(uuid)
      case None => UUID.randomUUID
    }
    val name = (request.body \ "name").as[String]
    if (model.createEntry(name)) {
      Ok(views.html.index(model.getFileListing(uuid))).withSession("uuid" -> uuid.toString)
    } else {
      Conflict("It is likely a file with the same name already exists.")
    }
  }

  def deleteFile: Action[JsValue] = Action(parse.json) { implicit request =>
    val uuid = request.session.get("uuid") match {
      case Some(uuid) => UUID.fromString(uuid)
      case None => UUID.randomUUID
    }
    val name = (request.body \ "name").as[String]
    if (model.deleteEntry(name)) {
      Ok(views.html.index(model.getFileListing(uuid))).withSession("uuid" -> uuid.toString)
    } else {
      NotFound
    }
  }
}