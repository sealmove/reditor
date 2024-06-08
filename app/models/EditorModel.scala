package models

import java.util.{Calendar, Date, UUID}
import javax.inject._
import play.api.Configuration
import play.api.db.Database
import anorm._
import anorm.SqlParser.{date, int, str, get}

sealed trait LockState
case object Owned extends LockState
case object Free extends LockState
case object Locked extends LockState
case class FileLink(name: String, state: LockState)

case class FileEntry(id: Int, name: String, content: Option[String], lock: Option[UUID], ts: Date)

@Singleton
class EditorModel @Inject()(db: Database, config: Configuration) {
  private[models] val cap = config.get[Int]("ALLOW_LOCK_ACQUISITION_TIMEOUT")
  private[models] val parser =
    int("id") ~
      str("file_name") ~
      str("file_text").? ~
      get[UUID]("user_lock").? ~
      date("ts") map {
      case id ~ name ~ content ~ lock ~ ts => FileEntry(id, name, content, lock, ts)
    }

  def calcState(x: FileEntry, uuid: UUID): LockState = {
    val now = Calendar.getInstance.getTime
    val diff = (now.getTime - x.ts.getTime) / 1000

    if (x.lock.isDefined && x.lock.get == uuid && diff <= cap) Owned
    else if (x.lock.isEmpty || diff > cap) Free
    else Locked
  }

  def getFileListing(uuid: UUID): Seq[FileLink] = db.withConnection { implicit conn =>
    val entries =
      SQL"""
      select * from files
      order by file_name asc
    """.as(parser.*)

    entries.map(x => FileLink(x.name, calcState(x, uuid)))
  }

  def createEntry(filename: String): Boolean = db.withConnection { implicit conn =>
    val entry = readEntry(filename)
    entry match {
      case Some(x) => false
      case None =>
        SQL"""
          insert into files (file_name)
          values ($filename)
        """.executeUpdate() == 1
    }
  }

  def readEntry(filename: String): Option[FileEntry] = db.withConnection { implicit conn =>
    val entries =
      SQL"""
      select * from files
      where file_name = $filename
    """.as(parser.*)

    entries.length match {
      case 1 => Some(entries.head)
      case _ => None
    }
  }

  def updateUUID(id: Int, uuid: UUID): Option[FileEntry] = db.withConnection { implicit conn =>
    val now = Calendar.getInstance.getTime
    val x = SQL"""
      update files
      set user_lock = $uuid::uuid,
          ts = $now
      where id = $id
    """.executeUpdate()
    if (x == 1) {
      SQL"""
        select * from files
        where id = $id
      """.as(parser.?.single)
    } else {
      None
    }
  }

  def updateContent(filename: String, content: String): Boolean = db.withConnection { implicit conn =>
    val now = Calendar.getInstance.getTime
    SQL"""
      update files
      set file_text = $content,
          ts = $now
      where file_name = $filename
    """.executeUpdate() == 1
  }

  def deleteEntry(filename: String): Boolean = db.withConnection { implicit conn =>
    val entry = readEntry(filename)
    entry.isDefined &&
      SQL"""
      delete from files
      where id = ${entry.get.id}
    """.executeUpdate() == 1
  }
}
