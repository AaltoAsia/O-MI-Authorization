package org.aalto.asia.database

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import akka.event.Logging
import scala.concurrent.{ Await, Future }

import akka.actor.ActorSystem

//import slick.driver.H2Driver.api._
import slick.jdbc.meta.MTable

import slick.basic.DatabaseConfig
//import slick.driver.H2Driver.api._
import slick.jdbc.JdbcProfile
import org.aalto.asia.types.Path
import org.aalto.asia.requests._

class AuthorizationDB(
  implicit
  val system: ActorSystem) extends AuthorizationTables {

  val dc: DatabaseConfig[JdbcProfile] = DatabaseConfig.forConfig[JdbcProfile](database.dbConfigName)
  import dc.profile.api._
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
          case "RULES" => permissionsTable.schema.create
        }
        DBIO.sequence(tableCreations.toSeq)
    }.flatMap {
      res =>
        groupsTable.filter(group => group.name inSet (expectedGroups)).map(_.name).result.flatMap {
          foundGroups: Seq[String] =>
            log.info(s"Found following groups: " + foundGroups.mkString(", "))
            val entries = expectedGroups.filter(!foundGroups.contains(_)).map {
              groupName => GroupEntry(None, groupName)
            }
            if (entries.nonEmpty) {
              log.info(entries.toString)
              groupsTable ++= entries
            } else {
              DBIO.successful(foundGroups.toSet)
            }
        }
    }
    val future = db.run(actions).flatMap {
      _ =>
        log.info(s"DBIOActions complete, checking existing tables.")
        db.run(
          currentTableNames.map {
            tableNames: Seq[String] =>
              val notCreated = expectedTables.filterNot {
                name: String => tableNames.contains(name)
              }
              if (notCreated.nonEmpty)
                log.error(s"Could not create following tables: $notCreated")
              else log.info(s"DB successfully initialised")
          }) //.flatMap(_ => logGroups)
    }
    future.failed.foreach {
      case t: Exception =>
        log.error(t.getMessage)
        throw t
    }
    Await.ready(future, 1.minutes)
  }

  initialise()

  def logGroups: Future[Unit] = {
    db.run(groupsTable.result.map {
      groups =>
        log.info(s"Found following groups from DB:\n${groups.mkString("\n")}")
    })
  }

  def logMembers: Future[Unit] = {
    db.run(membersTable.result.map {
      members =>
        log.info(s"Found following members from DB:\n${members.mkString("\n")}")
    })
  }
  def logPermissions: Future[Unit] = {
    db.run(permissionsTable.result.map {
      permissions =>
        log.info(s"Found following permissions from DB:\n${permissions.mkString("\n")}")
    })
  }
  def getPermissions(username: String, groups: Set[String], request: Request): Future[PermissionResult] = {
    db.run(getPermissionsIOA(username, groups, request))
  }
  def getUsers(group: Option[String]): Future[Set[String]] = {
    group.map {
      groupname =>
        getMembers(groupname)
    }.getOrElse {
      db.run(usersTable.map(_.name).result.map(_.toSet))
    }
  }
  def getGroups(user: Option[String]): Future[Set[String]] = {
    user.map {
      username =>
        val uid = usersTable.filter(_.name === username).map(_.userId)
        val groupIds = for {
          (gId, me) <- uid join membersTable on ((id, me) => id === me.userId)
        } yield (me.groupId)
        val groups = for {
          (gId, group) <- groupIds join groupsTable on ((id, ge) => id === ge.groupId)
        } yield (group.name)
        db.run(groups.result.map(_.toSet))
    }.getOrElse {
      db.run(groupsTable.map(_.name).result.map(_.toSet))
    }
  }
  def getMembers(groupname: String): Future[Set[String]] = {
    val groupIds = groupsTable.filter(_.name === groupname).map(_.groupId)
    val memberIds = for {
      (gId, me) <- groupIds join membersTable on ((gId, me) => gId === me.groupId)
    } yield (me.userId)
    val users = for {
      (mId, ue) <- memberIds join usersTable on ((mId, ue) => mId === ue.userId)
    } yield (ue.name)
    db.run(users.result.map(_.toSet))
  }

  def newUser(username: String): Future[Unit] = {
    val groupname = s"${username}_USERGROUP"
    val createUser = { usersTable += UserEntry(None, username) }
    val createUserGroup = { groupsTable += GroupEntry(None, groupname) }

    val action = DBIO.sequence(Vector(createUser, createUserGroup)).flatMap {
      res =>
        groupsTable.result.map {
          groups =>
            log.info(s"Found following groups from DB:\n${groups.mkString("\n")}")
        }.flatMap {
          _ =>
            membersTable.result.map {
              members =>
                log.info(s"Found following members from DB:\n${members.mkString("\n")}")
            }.flatMap {
              _ => joinGroupsAction(username, Set(groupname, "DEFAULT"))
            }
        }
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
  /*
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
  }*/
  def removePermissions(groupname: String, permissions: Seq[RPermission]): Future[Unit] = {
    val action = groupsTable.filter { row => row.name === groupname }.map(_.groupId).result.flatMap {
      groupIds: Seq[Long] =>
        groupIds.headOption match {
          case Some(groupId) =>
            DBIO.sequence(permissions.map {
              case RPermission(path, allow) =>
                permissionsTable.filter {
                  permission => permission.groupId === groupId && permission.path === path && permission.allow === allow
                }.delete
            }.toSeq)
          case None =>
            slick.dbio.DBIOAction.failed(new Exception(s"Unknown group named $groupname"))
        }
    }
    db.run(action).map { _ => Unit }
  }

  def setPermissionsForPaths(groupname: String, pathPermissions: Seq[Permission]): Future[Unit] = {
    val action = groupsTable.filter { row => row.name === groupname }.map(_.groupId).result.flatMap {
      groupIds: Seq[Long] =>
        groupIds.headOption match {
          case Some(groupId) =>
            val insertOrUpdates = pathPermissions.map {
              case Permission(path: Path, request: Request, allow: Boolean) =>
                val existsQ = permissionsTable.filter {
                  row =>
                    row.path === path &&
                      row.groupId === groupId &&
                      row.allow === allow
                }
                existsQ.result.flatMap {
                  permissions =>
                    permissions.headOption match {
                      case None => { permissionsTable += PermissionEntry(groupId, request.toString, allow, path) }
                      case Some(permission) => { existsQ.map(_.request).update(request.toString) }
                    }
                }

            }
            DBIO.sequence(insertOrUpdates)
          case None =>
            slick.dbio.DBIOAction.failed(new Exception(s"Unknown group named $groupname"))
        }
    }
    db.run(action).map { _ => Unit }
  }
}
