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
import org.aalto.asia.requests._

import Request._

case class AuthEntry(
  val groupId: Option[Long],
  val request: Int, //Bit flag
  val allow: Boolean,
  val path: Path,
  val expire: Timestamp)

case class UserEntry(
  val id: Option[Long],
  val name: String)

case class GroupEntry(
  val id: Option[Long],
  val name: String)

case class MemberEntry(
  val groupId: Long,
  val userId: Long,
  val expire: Timestamp)

case class SubGroupEntry(
  val groupId: Long,
  val subGroupId: Long,
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

  class UsersTable(tag: Tag) extends Table[UserEntry](tag, "USERS") {
    def userId: Rep[Long] = column[Long]("USER_ID", O.PrimaryKey, O.AutoInc)
    def name: Rep[String] = column[String]("NAME")

    def nameIndex = index("NAME_INDEX", name, unique = true)

    def * = (userId?, name) <> (UserEntry.tupled, UserEntry.unapply)
  }

  class Users extends TableQuery[UsersTable](new UsersTable(_))
  val usersTable = new Users()

  class GroupsTable(tag: Tag) extends Table[GroupEntry](tag, "GROUPS") {
    def groupId: Rep[Long] = column[Long]("GROUP_ID", O.PrimaryKey, O.AutoInc)
    def name: Rep[String] = column[String]("NAME")

    def nameIndex = index("NAME_INDEX", name, unique = true)

    def * = (groupId?, name) <> (GroupEntry.tupled, GroupEntry.unapply)
  }

  class Groups extends TableQuery[GroupsTable](new GroupsTable(_))
  val groupsTable = new Groups()

  class MembersTable(tag: Tag) extends Table[MemberEntry](tag, "MEMBERS") {
    def groupId: Rep[Long] = column[Long]("GROUP_ID")
    def userId: Rep[Long] = column[Long]("USER_ID")
    def userIndex = index("USER_INDEX", userId, unique = false)
    def groupIndex = index("GROUP_INDEX", groupId, unique = false)
    def groupsFK = foreignKey("GROUP_FK", groupId, groupsTable)(_.groupId, onUpdate = ForeignKeyAction.Restrict, onDelete = ForeignKeyAction.Cascade)
    def usersFK = foreignKey("USER_FK", userId, usersTable)(_.userId, onUpdate = ForeignKeyAction.Restrict, onDelete = ForeignKeyAction.Cascade)
    def * = (groupId, userId) <> (MemberEntry.tupled, MemberEntry.unapply)
  }
  class Members extends TableQuery[MembersTable](new MembersTable(_))
  val membersTable = new Members()

  class SubGroupsTable(tag: Tag) extends Table[SubGroupEntry](tag, "SUBGROUPS") {
    def groupId: Rep[Long] = column[Long]("GROUP_ID")
    def subGroupId: Rep[Long] = column[Long]("SUB_GROUP_ID")
    def subGroupIndex = index("SUB_GROUP_INDEX", userId, unique = false)
    def groupIndex = index("GROUP_INDEX", groupId, unique = false)
    def groupsFK = foreignKey("GROUP_FK", groupId, groupsTable)(_.groupId, onUpdate = ForeignKeyAction.Restrict, onDelete = ForeignKeyAction.Cascade)
    def subgroupsFK = foreignKey("SUBGROUP_FK", subGroupId, groupsTable)(_.groupId, onUpdate = ForeignKeyAction.Restrict, onDelete = ForeignKeyAction.Cascade)
    def * = (groupId, subGroupId) <> (SubGroupEntry.tupled, SubGroupEntry.unapply)
  }
  class SubGroups extends TableQuery[SubGroupsTable](new SubGroupsTable(_))
  val subGroupsTable = new SubGroups()

  class RulesTable(tag: Tag) extends Table[RuleEntry](tag, "RULES") {
    def groupId: Rep[Long] = column[Long]("GROUP_ID")
    def request: Rep[Int] = column[Int]("REQUEST")
    def path: Rep[Path] = column[Path]("PATH")
    def allow: Rep[Boolean] = column[Boolean]("ALLOW_OR_DENY")
    def expire: Rep[Timestamp] = column[Timestamp]("EXPIRE")

    def groupIndex = index("GROUP_INDEX", groupId, unique = false)
    def groupRequestIndex = index("GROUP_REQUEST_INDEX", (groupId, request), unique = false)

    def groupsFK = foreignKey("GROUP_FK", groupId, groups)(_.groupId, onUpdate = ForeignKeyAction.Restrict, onDelete = ForeignKeyAction.Cascade)
    def * = (groupId?, request, allow, path, expire) <> (AuthEntry.tupled, AuthEntry.unapply)
  }

  class Rules extends TableQuery[RulesTable](new RulesTable(_))
  val rulesTable = new Rules()

  def currentTimestamp: Timestamp = new Timestamp(new Date().getTime())
  protected def queryUserRulesForRequest(username: String, request: Request) = {
    val user = usersTable.filter { row => row.name == username }
    val groups = for {
      (user, member) <- user join membersTable on { (user, memberEntry) => memberEntry.userId === user.userId }
    } yield (member.groupId)
    def tmp(groupIds: Set[Long]): DBIOro[Set[Long]] = {
      subGroups.filter {
        row => row.subGroupId inSet (groupIds)
      }.result.map {
        parentGroupIds: Seq[Long] =>
          groupIds ++ parentGroupIds.toSet
      }.flatMap {
        gIds: Set[Long] =>
          if (gIds.lenght > groupIds.lenght) {
            tmp(gIds)
          } else {
            DBIOAction.successful(gIds)
          }
      }
    }
    groups.result.flatMap {
      groupIds: Seq[Long] =>
        tmp(groupIds.toSet)
    }
  }
}
