package org.aalto.asia.database

import java.util.Date
import java.sql.Timestamp

import scala.util.{ Try, Success, Failure }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import akka.event.Logging
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
import org.aalto.asia.types.Path
import org.aalto.asia.AuthConfigSettings
import org.aalto.asia.requests._

class AuthorizationDB(
  implicit
  val system: ActorSystem) extends AuthorizationTables {

  val dc: DatabaseConfig[JdbcProfile] = DatabaseConfig.forConfig[JdbcProfile](database.dbConfigName)
  import dc.driver.api._
  import dc.driver.api.DBIOAction
  val db: Database = dc.db
  lazy val log = Logging(system, classOf[AuthorizationDB])

  def currentTableNames: DBIOro[Vector[String]] = MTable.getTables.map {
    mts =>
      mts.map {
        mt => mt.name.name
      }
  }

  def initialise(): Unit = {
    val expectedGroups = Set("DEFAULT", "ADMIN")
    val expectedTables = Set("USERS", "GROUPS", "MEMBERS", "RULES")
    val actions = currentTableNames.flatMap {
      tableNames: Seq[String] =>
        val tableCreations = expectedTables.filterNot {
          name: String =>
            tableNames.contains(name)
        }.map {
          case "USERS" => usersTable.schema.create
          case "GROUPS" => groupsTable.schema.create
          case "MEMBERS" => membersTable.schema.create
          case "RULES" => rulesTable.schema.create
        }
        DBIO.sequence(tableCreations.toSeq)
    }.flatMap {
      res =>
        groupsTable.filter(group => group.name inSet (expectedGroups)).result.flatMap {
          foundGroups: Seq[GroupEntry] =>
            val entries = expectedGroups.filter(!foundGroups.contains(_)).map {
              groupName => GroupEntry(None, groupName)
            }
            if (entries.nonEmpty) {
              groupsTable ++= entries
            } else {
              DBIO.successful(foundGroups.toSet)
            }
        }
    }
    db.run(actions)
  }
  initialise()

  def logMembers: Future[Unit] = {
    db.run(membersTable.result.map {
      members =>
        log.info(s"Found following members from DB:\n${members.mkString("\n")}")
    })
  }
  def logRules: Future[Unit] = {
    db.run(rulesTable.result.map {
      rules =>
        log.info(s"Found following rules from DB:\n${rules.mkString("\n")}")
    })
  }
  def userRulesForRequest(username: String, request: Request): Future[PermissionResult] = {
    logMembers.flatMap(_ =>
      logRules.flatMap(
        _ =>
          db.run(queryUserRulesForRequest(username, request))))
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
  def removeUser(username: String): Future[Unit] = {
    db.run(usersTable.filter(row => row.name === username).delete.flatMap {
      _ =>
        val groupname = s"${username}_USERGROUP"
        groupsTable.filter(row => row.name === groupname).delete

    }).map(_ => Unit)
    //Triggers should remove any row referinc with foreing key
  }
  def removeGroup(groupname: String): Future[Unit] = {
    groupname match {
      case "DEFAULT" => Future.successful()
      case "ADMIN" => Future.successful()
      case usergroup: String if usergroup.endsWith("_USERGROUP") =>
        Future.failed(new Exception("Trying to remove a USERGROUP"))
      case group: String =>
        db.run(groupsTable.filter(row => row.name === groupname).delete).map(_ => Unit)
      //Triggers should remove any row referinc with foreing key
    }
  }
  def joinGroups(username: String, groups: Set[String]): Future[Unit] = {
    db.run(joinGroupsAction(username, groups)).map(_ => Unit)
  }
  def leaveGroups(username: String, groups: Set[String]): Future[Unit] = {
    val user = usersTable.filter(row => row.name === username).map(_.userId).result
    val groupidsIO = groupsTable.filter(row => row.name inSet (groups)).map(_.groupId).result
    val action = user.flatMap {
      uids: Seq[Long] =>
        uids.headOption match {
          case Some(uid: Long) =>
            groupidsIO.flatMap {
              groupids =>
                membersTable.filter {
                  row =>
                    row.userId === uid &&
                      (row.groupId inSet (groupids))
                }.delete
            }
          case None =>
            DBIO.failed(new Exception(s"Unknown user $username"))
        }
    }
    db.run(action).map(_ => Unit)
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
    db.run(action).map(_ => Unit)
  }
  def exludeSubGroup(subgroup: String, group: String): Future[Unit] = {
    val ids = for {
      sgroup <- groupsTable.filter { row => row.name === subgroup }
      group <- groupsTable.filter { row => row.name === group }
    } yield (group.groupId, sgroup.groupId)
    val action = ids.result.flatMap {
      tuples =>
        val removes = tuples.map {
          case (gid, sgid) =>
            subGroupsTable.filter {
              row => row.groupId === gid && row.subGroupId === sgid
            }.result
        }
        DBIO.sequence(removes)
    }
    db.run(action).map(_ => Unit)
  }

  def setRulesForPaths(groupname: String, pathRules: Seq[Rule]): Future[Unit] = {
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
