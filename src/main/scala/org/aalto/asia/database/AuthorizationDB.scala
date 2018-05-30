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
import org.aalto.asia.requests._

class AuthorizationDB(
  implicit
  val system: ActorSystem,
  implicit val settings: AuthConfigSettings) extends AuthorizationTables {
  val dc: DatabaseConfig[JdbcProfile] = DatabaseConfig.forConfig[JdbcProfile](database.dbConfigName)
  import dc.driver.api._
  import dc.driver.api.DBIOAction
  val db: Database = dc.db

  def initialise(): Unit = {
    val expected = Set("DEFAULT", "ADMIN")
    val actions = groupsTable.filter(group => group.name inSet (expected)).result.flatMap {
      foundGroups: Seq[GroupEntry] =>
        val entries = expected.filter(!foundGroups.contains(_)).map {
          groupName => GroupEntry(None, groupName)
        }
        if (entries.nonEmpty) {
          groupsTable ++= entries
        } else {
          DBIO.successful(foundGroups.toSet)
        }
    }
    db.run(actions)
  }
  initialise()

  def userRulesForRequest(username: String, request: Request): Future[PermissionResult] = {
    db.run(queryUserRulesForRequest(username, request))
  }

  def newUser(username: String): Future[Int] = {
    val action = { usersTable += UserEntry(None, username) }
    val r = db.run(action)
    r.flatMap {
      i =>
        newGroup(s"${username}_GROUP").flatMap {
          u =>
            joinGroup(username, s"${username}_GROUP").map(_ => i)
        }
    }
  }

  def newGroup(groupname: String): Future[Int] = {
    val action = { groupsTable += GroupEntry(None, groupname) }
    val r = db.run(action)
    r
  }
  def joinGroup(username: String, group: String) = {
    val ids = for {
      user <- usersTable.filter { row => row.name === username }
      group <- groupsTable.filter { row => row.name === group }
    } yield (group.groupId, user.userId)
    val action = ids.result.flatMap {
      tuples =>
        val entries = tuples.map {
          case (gid, uid) => MemberEntry(gid, uid)
        }
        membersTable ++= entries
    }
    val r = db.run(action)
    r
  }
  def newSubGroup(subgroup: String, group: String) = {
    val ids = for {
      sgroup <- groupsTable.filter { row => row.name === subgroup }
      group <- groupsTable.filter { row => row.name === group }
    } yield (group.groupId, sgroup.groupId)
    val action = ids.result.flatMap {
      tuples =>
        val entries = tuples.map {
          case (gid, sgid) => SubGroupEntry(gid, sgid)
        }
        subGroupsTable ++= entries
    }
    val r = db.run(action)
    r
  }

  def newRulesForPaths(groupname: String, request: Request, allowOrDeny: Boolean, paths: Seq[Path]): Future[Option[Int]] = {
    val action = groupsTable.filter { row => row.name === groupname }.map(_.groupId).result.flatMap {
      ids: Seq[Long] =>
        ids.headOption match {
          case Some(id) =>
            //TODO: Check and update for old rules
            val entries = paths.map {
              path =>
                RuleEntry(
                  id,
                  request.toString,
                  allowOrDeny,
                  path)
            }
            rulesTable ++= entries
          case None =>
            slick.dbio.DBIOAction.failed(new Exception(s"Unknown role named $groupname"))
        }
    }
    db.run(action)
  }
}
