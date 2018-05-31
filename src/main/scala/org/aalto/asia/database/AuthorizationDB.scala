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
    //TODO: How to make group hierachy affect permissions
    db.run(queryUserRulesForRequest(username, request))
  }

  def newUser(username: String): Future[Unit] = {
    val groupname = s"${username}_USERGROUP"
    val createUser = { usersTable += UserEntry(None, username) }
    val createUserGroup = { groupsTable += GroupEntry(None, groupname) }

    val action = DBIO.sequence(Vector(createUser, createUserGroup)).flatMap {
      res => joinGroupsAction(username, Set(groupname, "DEFAULT"))
    }
    db.run(action).map(_ => Unit)
  }

  def newGroup(groupname: String): Future[Unit] = {
    val action = { groupsTable += GroupEntry(None, groupname) }
    db.run(action).map(_ => Unit)
  }
  def joinGroups(username: String, groups: Set[String]): Future[Unit] = {
    db.run(joinGroupsAction(username, groups)).map(_ => Unit)
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

  def setRulesForPaths(groupname: String, pathRules: Seq[Rule]): Future[Unit] = {
    //TODO
    val action = groupsTable.filter { row => row.name === groupname }.map(_.groupId).result.flatMap {
      groupIds: Seq[Long] =>
        groupIds.headOption match {
          case Some(groupId) =>
            val insertOrUpdates = pathRules.map {
              case Rule(path: Path, request: Request, allow: Boolean) =>
                val existsQ = rulesTable.filter {
                  row =>
                    row.path === path &&
                      row.groupId === groupId &&
                      row.allow === allow
                }
                existsQ.result.flatMap {
                  rules =>
                    rules.headOption match {
                      case None => { rulesTable += RuleEntry(groupId, request.toString, allow, path) }
                      case Some(rule) => { existsQ.map(_.request).update(request.toString) }
                    }
                }

            }
            DBIO.sequence(insertOrUpdates)
          case None =>
            slick.dbio.DBIOAction.failed(new Exception(s"Unknown role named $groupname"))
        }
    }
    db.run(action).map { _ => Unit }
  }
}
