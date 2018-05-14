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

import akka.actor.ActorSystem

import org.slf4j.LoggerFactory
//import slick.driver.H2Driver.api._
import slick.jdbc.meta.MTable

import slick.backend.DatabaseConfig
//import slick.driver.H2Driver.api._
import slick.driver.JdbcProfile
import slick.lifted.{ Index, ForeignKeyQuery, ProvenShape }
import types.Path
import org.aalto.asia.AuthConfigSettings

class AuthorizationDB(
  implicit
  val system: ActorSystem,
  implicit val settings: AuthConfigSettings) extends AuthorizationTables {
  val dc: DatabaseConfig[JdbcProfile] = DatabaseConfig.forConfig[JdbcProfile](database.dbConfigName)
  import dc.driver.api._
  import dc.driver.api.DBIOAction
  val db: Database = dc.db

  def initialise(): Unit = {
    val actions = roles.filter(role => role.name === "DEFAULT").result.flatMap {
      foundRoles: Seq[RoleEntry] =>
        foundRoles.headOption match {
          case Some(role) =>
            slick.dbio.DBIOAction.successful(role.roleID)
          case None =>
            val expire: Timestamp = new Timestamp(Long.MaxValue)
            val insert = { roles += RoleEntry(None, "DEFAULT", expire) }
            insert
        }
    }
    db.run(actions)
  }
  initialise()

  def userRulesForRequest(user_name: String, request: Request): Future[PermissionResult] = {
    db.run(queryUserRulesForRequest(user_name, request).result.map {
      case ars: Seq[AuthEntry] if ars.isEmpty =>
        throw new Exception("No rules for role or unknown role")
      case ars: Seq[AuthEntry] =>
        val (deniedAR, allowedAR) = ars.partition(_.allow)
        PermissionResult(
          user_name,
          allowedAR.map(_.path),
          deniedAR.map(_.path))
    })
  }
  def newRole(role_name: String, expireO: Option[Timestamp]): Future[Option[Int]] = {
    val expire: Timestamp = expireO.getOrElse(new Timestamp(Long.MaxValue))
    val action = { roles += RoleEntry(None, role_name, expire) }
    val r = db.run(action)
    r.flatMap {
      case id: Int => addToGroups(role_name, Vector("DEFAULT"), None)
    }
  }
  def addToGroups(name: String, groups: Seq[String], expireO: Option[Timestamp]): Future[Option[Int]] = {
    val expire: Timestamp = expireO.getOrElse(new Timestamp(Long.MaxValue))
    val user_id = roles.filter { role => role.name === name }.result
    val groups_id = roles.filter { role => role.name.inSet(groups.toSet) }.result
    val actions = user_id.flatMap {
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
    db.run(actions)

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
