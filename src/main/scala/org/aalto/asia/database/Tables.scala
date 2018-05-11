package org.aalto.asia.database

import java.util.Date
import java.sql.Timestamp

import scala.util.{ Try, Success, Failure }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }
import scala.collection.mutable.{ Map => MutableMap, HashMap => MutableHashMap }
import scala.collection.immutable.BitSet.BitSet1
import scala.language.postfixOps

import org.slf4j.LoggerFactory
//import slick.driver.H2Driver.api._
import slick.jdbc.meta.MTable

import slick.backend.DatabaseConfig
//import slick.driver.H2Driver.api._
import slick.driver.JdbcProfile
import slick.lifted.{ Index, ForeignKeyQuery, ProvenShape }
import types.Path

sealed trait Request {
  def mask: Int
}

case class Read() extends Request {
  val mask: Int = 1
}

case class Write() extends Request {
  val mask: Int = 2
}
case class ReadWrite() extends Request {
  val mask: Int = 2 | 1
}

case class PermissionResult(
  allowed: Seq[Path],
  denied: Seq[Path])

object Request {
  def apply(mask: Int): Request = {
    mask match {
      case 1 => Read()
      case 2 => Write()
      case 3 => ReadWrite()
    }
  }
}
import Request._

case class AuthEntry(
  val roleid: Option[Long],
  val request: Int, //Bit flag
  val allow: Boolean,
  val path: Path,
  val expire: Timestamp)

case class RoleEntry(
  val roleID: Option[Long],
  val name: String,
  val expire: Timestamp)

case class MemberEntry(
  val roleID: Long,
  val memberID: Long,
  val expire: Timestamp)

trait DBBase {
  val dc: DatabaseConfig[JdbcProfile] //= DatabaseConfig.forConfig[JdbcProfile](database.dbConfigName)
  import dc.driver.api._
  val db: Database
  //protected[this] val db: Database
}

trait Tables extends DBBase {
  import dc.driver.api._
  import dc.driver.api.DBIOAction

  type DBSIOro[Result] = DBIOAction[Seq[Result], Streaming[Result], Effect.Read]
  type DBIOro[Result] = DBIOAction[Result, NoStream, Effect.Read]
  type DBIOwo[Result] = DBIOAction[Result, NoStream, Effect.Write]
  type DBIOsw[Result] = DBIOAction[Result, NoStream, Effect.Schema with Effect.Write]
  type ReadWrite = Effect with Effect.Write with Effect.Read with Effect.Transactional
  type DBIOrw[Result] = DBIOAction[Result, NoStream, ReadWrite]

  implicit lazy val pathColumnType = MappedColumnType.base[Path, String](
    { p: Path => p.toString },
    { str: String => Path(str) } // String to Path
  )

  implicit val requestColumnType = MappedColumnType.base[Request, Int](
    { p: Request => p.mask },
    { i: Int => Request(i) } // String to Path
  )

  class RolesTable(tag: Tag) extends Table[RoleEntry](tag, "ROLES") {
    def roleid: Rep[Long] = column[Long]("ROLEID", O.PrimaryKey, O.AutoInc)
    def name: Rep[String] = column[String]("NAME")
    def expire: Rep[Timestamp] = column[Timestamp]("EXPIRE")

    def nameIndex = index("NAMEINDEX", name, unique = true)

    def * = (roleid?, name, expire) <> (RoleEntry.tupled, RoleEntry.unapply)
  }

  class Roles extends TableQuery[RolesTable](new RolesTable(_))
  val roles = new Roles()

  class MembersTable(tag: Tag) extends Table[MemberEntry](tag, "MEMBERS") {
    def roleid: Rep[Long] = column[Long]("ROLEID")
    def memberid: Rep[Long] = column[Long]("MEMBERID")
    def expire: Rep[Timestamp] = column[Timestamp]("EXPIRE")

    def roleIndex = index("ROLEINDEX", roleid, unique = false)
    def memberIndex = index("MEMBERINDEX", memberid, unique = false)

    def rolesFK = foreignKey("ROLE_FK", roleid, roles)(_.roleid, onUpdate = ForeignKeyAction.Restrict, onDelete = ForeignKeyAction.Cascade)
    def membersFK = foreignKey("MEMBER_FK", memberid, roles)(_.roleid, onUpdate = ForeignKeyAction.Restrict, onDelete = ForeignKeyAction.Cascade)

    def * = (roleid, memberid, expire) <> (MemberEntry.tupled, MemberEntry.unapply)
  }

  class Members extends TableQuery[MembersTable](new MembersTable(_)) {
    def rolesOf(roleId: Long): DBSIOro[Long] = rolesOfQ(roleId).result
    protected def rolesOfQ(roleId: Long) = this.filter(row => row.memberid === roleId).map(_.roleid)
  }
  val members = new Members()

  class AuthorizationTable(tag: Tag) extends Table[AuthEntry](tag, "AUTHENTRIES") {
    def roleid: Rep[Long] = column[Long]("ROLEID")
    def request: Rep[Int] = column[Int]("REQUEST")
    def path: Rep[Path] = column[Path]("PATH")
    def allow: Rep[Boolean] = column[Boolean]("ALLOW_OR_DENY")
    def expire: Rep[Timestamp] = column[Timestamp]("EXPIRE")

    def roleIndex = index("ROLEINDEX", roleid, unique = false)
    def roleRequestIndex = index("ROLEREQUESTINDEX", (roleid, request), unique = false)

    def rolesFK = foreignKey("ROLE_FK", roleid, roles)(_.roleid, onUpdate = ForeignKeyAction.Restrict, onDelete = ForeignKeyAction.Cascade)
    def * = (roleid?, request, allow, path, expire) <> (AuthEntry.tupled, AuthEntry.unapply)
  }

  class Authorizations extends TableQuery[AuthorizationTable](new AuthorizationTable(_)) {

    def selectByRole(roleId: Long): DBSIOro[AuthEntry] = selectByRoleQ(roleId).result
    //TODO:How to filter with request?
    //def selectByRoleAndRequest(roleId: Long,request:Request) = this.filter{ row => row.roleid === roleId && row.request.contains(request)}
    protected def selectByRoleQ(roleId: Long) = this.filter { row => row.roleid === roleId }

    def selectByRoles(roleIds: Seq[Long]): DBSIOro[AuthEntry] = selectByRolesQ(roleIds).result
    protected def selectByRolesQ(roleIds: Seq[Long]) = this.filter { row => row.roleid inSet roleIds.toSet }
  }
  val authRules = new Authorizations()

  def currentTimestamp: Timestamp = new Timestamp(new Date().getTime())
  protected def queryUserRulesForRequest(user_name: String, request: Request) = {
    val rolesOfUser = for {
      (role, member) <- (roles.filter { row => row.name === user_name } join members.filter(_.expire >= currentTimestamp) on { (role, member) => member.memberid === role.roleid })
    } yield (member.roleid)
    val rulesForRoles = for {
      (id, rule) <- rolesOfUser join authRules.filter(_.expire >= currentTimestamp) on { (id, rule) => id === rule.roleid }
    } yield (rule)
    //TODO: How to use masking correctly?
    val rulesForRequest = rulesForRoles.filter {
      rule =>
        rule.expire >= currentTimestamp && (
          rule.request === request.mask ||
          rule.request === (request.mask | 1) ||
          rule.request === (request.mask | 2))
    }
    rulesForRequest
  }
  def userRulesForRequest(user_name: String, request: Request): Future[PermissionResult] = {
    db.run(queryUserRulesForRequest(user_name, request).result.map {
      ars: Seq[AuthEntry] =>
        val (deniedAR, allowedAR) = ars.partition(_.allow)
        PermissionResult(
          allowedAR.map(_.path),
          deniedAR.map(_.path))
    })
  }
  def newRole(role_name: String, expireO: Option[Timestamp]): Future[Int] = {
    val expire: Timestamp = expireO.getOrElse(new Timestamp(Long.MaxValue))
    val action = { roles += RoleEntry(None, role_name, expire) }
    db.run(action)
  }
  def addToGroups(name: String, groups: Seq[String], expireO: Option[Timestamp]) = {
    val expire: Timestamp = expireO.getOrElse(new Timestamp(Long.MaxValue))
    val user_id = roles.filter { role => role.name === name }.result
    val groups_id = roles.filter { role => role.name.inSet(groups.toSet) }.result
    user_id.flatMap {
      role: Seq[RoleEntry] =>
        role.headOption match {
          case Some(RoleEntry(Some(id), _, _)) =>
            groups_id.flatMap {
              groupEntries: Seq[RoleEntry] =>
                if (groupEntries.size == groups.size) {
                  val entries = groupEntries.map {
                    gEntry => MemberEntry(gEntry.roleID.get, id, expire)
                  }
                  members ++= entries
                } else {
                  val notFound = groups.filterNot {
                    g_name: String => groupEntries.exists(_.name == g_name)
                  }
                  slick.dbio.DBIOAction.failed(new Exception(s"Unknown groups: ${notFound.mkString(", ")}"))
                }
            }
          case None =>
            slick.dbio.DBIOAction.failed(new Exception(s"Unknown role named $name"))
        }
    }

  }
  def newRules(role_name: String, request: Request, allowOrDeny: Boolean, paths: Seq[Path], expireO: Option[Timestamp]): Future[Option[Int]] = {
    val expire: Timestamp = expireO.getOrElse(new Timestamp(Long.MaxValue))
    val action = roles.filter { role => role.name === role_name }.map(_.roleid).result.flatMap {
      ids: Seq[Long] =>
        ids.headOption match {
          case Some(id) =>
            val entries = paths.map {
              path =>
                AuthEntry(
                  Some(id),
                  request.mask,
                  allowOrDeny,
                  path,
                  expire)
            }
            authRules ++= entries
          case None =>
            slick.dbio.DBIOAction.failed(new Exception(s"Unknown role named $role_name"))
        }
    }
    db.run(action)
  }
}
