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
import org.aalto.asia.RequestType

import slick.backend.DatabaseConfig
//import slick.driver.H2Driver.api._
import slick.driver.JdbcProfile
import slick.lifted.{ Index, ForeignKeyQuery, ProvenShape }
import types.Path

case class PermissionResult(
  roles: Set[String],
  allowed: Seq[Path],
  denied: Seq[Path])

sealed trait Request {
  def mask: Int
}

case class Read() extends Request {
  val mask: Int = 1
}

case class Write() extends Request {
  val mask: Int = 2
}
case class Call() extends Request {
  val mask: Int = 4
}
case class Delete() extends Request {
  val mask: Int = 8
}
case class ReadWrite() extends Request {
  val mask: Int = 2 | 1
}
case class ReadCall() extends Request {
  val mask: Int = 4 | 1
}
case class ReadDelete() extends Request {
  val mask: Int = 8 | 1
}
case class WriteDelete() extends Request {
  val mask: Int = 8 | 2
}
case class WriteCall() extends Request {
  val mask: Int = 4 | 2
}
case class CallDelete() extends Request {
  val mask: Int = 8 | 4
}
case class ReadWriteCall() extends Request {
  val mask: Int = 2 | 1 | 4
}
case class ReadWriteDelete() extends Request {
  val mask: Int = 2 | 1 | 8
}
case class ReadCallDelete() extends Request {
  val mask: Int = 4 | 1 | 8
}
case class WriteCallDelete() extends Request {
  val mask: Int = 2 | 4 | 8
}
case class ReadWriteCallDelete() extends Request {
  val mask: Int = 2 | 1 | 4 | 8
}

import RequestType._
object Request {
  def apply(requestType: RequestType): Request = {
    requestType match {
      case RequestType.Read => Read()
      case RequestType.Write => Write()
      case RequestType.Call => Call()
      case RequestType.Delete => Delete()
    }
  }
  def apply(mask: Int): Request = {
    mask match {
      case 1 => Read()
      case 2 => Write()
      case 3 => ReadWrite()
      case 4 => Call()
      case 5 => ReadCall()
      case 8 => Delete()
      case 9 => ReadDelete()
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

trait AuthorizationTables extends DBBase {
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
  protected def queryUserRulesForRequest(role_names: Set[String], request: Request) = {
    val roleIds = roles.filter { row => row.name inSet role_names } map (_.roleid)
    val rulesForRoles = for {
      (id, rule) <- roleIds join authRules.filter(_.expire >= currentTimestamp) on { (id, rule) => id === rule.roleid }
    } yield (rule)
    val rulesForRequest = rulesForRoles.filter {
      rule =>
        rule.expire >= currentTimestamp && (
          rule.request === request.mask ||
          rule.request === (request.mask | 1) ||
          rule.request === (request.mask | 2))
    }
    rulesForRequest
  }
}
