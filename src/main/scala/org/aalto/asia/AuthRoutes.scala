package org.aalto.asia

import akka.actor.{ ActorRef, ActorSystem }
import akka.event.{ LoggingAdapter, Logging }

import scala.util.control.NonFatal
import scala.concurrent.duration._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.MethodDirectives.delete
import akka.http.scaladsl.server.directives.MethodDirectives.get
import akka.http.scaladsl.server.directives.MethodDirectives.post
import akka.http.scaladsl.server.directives.RouteDirectives.complete
import akka.http.scaladsl.server.directives.PathDirectives.path

import scala.concurrent.Future
import akka.pattern.ask
import akka.util.Timeout
import database._
import org.aalto.asia.requests._

trait AuthRoutes extends JsonSupport {

  implicit val system: ActorSystem
  val authDB: AuthorizationDB
  import system.dispatcher

  lazy val log = Logging(system, classOf[AuthRoutes])

  // Required by the `ask` (?) method below
  implicit lazy val timeout = Timeout(5.seconds) // usually we'd obtain the timeout from the system's configuration

  lazy val routes = pathPrefix("v1") {
    post {
      path("get-permissions") {
        entity(as[GetPermissions]) { pr =>
          val permissions: Future[PermissionResult] = authDB.getPermissions(pr.username, pr.groups, pr.request)
          permissions.onFailure {
            case t: Throwable =>
              log.error(t.getMessage)
          }
          complete(permissions)
        }
      } ~ path("add-user") {
        entity(as[AddUser]) { ar: AddUser =>
          val result: Future[Unit] = authDB.newUser(ar.username)
          complete(result)
        }
      } ~ path("remove-user") {
        entity(as[RemoveUser]) { ar: RemoveUser =>
          val result: Future[Unit] = authDB.removeUser(ar.username)
          complete(result)
        }
      } ~ path("add-group") {
        entity(as[AddGroup]) { ar: AddGroup =>
          val result: Future[Unit] = authDB.newGroup(ar.groupname)
          complete(result)
        }
      } ~ path("remove-group") {
        entity(as[RemoveGroup]) { ar: RemoveGroup =>
          val result: Future[Unit] = authDB.removeGroup(ar.groupname)
          complete(result)
        }
      } ~ path("join-groups") {
        entity(as[JoinGroups]) { ar: JoinGroups =>
          val result: Future[Unit] = authDB.joinGroups(ar.username, ar.groups)
          complete(result)
        }
      } ~ path("leave-groups") {
        entity(as[LeaveGroups]) { ar: LeaveGroups =>
          val result: Future[Unit] = authDB.joinGroups(ar.username, ar.groups)
          complete(result)
        }
      } ~ path("set-permissions") {
        entity(as[SetPermissions]) { ar: SetPermissions =>
          val result: Future[Unit] = authDB.setPermissionsForPaths(ar.group, ar.rules)
          complete(result)
        }
      } ~ path("remove-permission") {
        entity(as[RemovePermissions]) { ar: RemovePermissions =>
          val result: Future[Unit] = authDB.removePermissions(ar.group, ar.rules)
          complete(result)
        }
      } ~ path("get-members") {
        entity(as[GetMembers]) { gm: GetMembers =>
          complete(authDB.getMembers(gm.groupname))
        }
      } ~ path("get-groups") {
        entity(as[GetGroups]) { gg: GetGroups =>
          complete(authDB.getGroups(gg.username))
        } ~ complete(authDB.getGroups(None))
      } ~ path("get-users") {
        entity(as[GetUsers]) { gu: GetUsers =>
          complete(authDB.getUsers(gu.groupname))
        } ~ complete(authDB.getUsers(None))
      }

    } ~ get {
      path("get-users") {
        parameters("groupname".?) { (groupname: Option[String]) =>
          complete(authDB.getUsers(groupname))
        }
      } ~ path("get-groups") {
        parameters("username".?) { (username: Option[String]) =>
          complete(authDB.getGroups(username))
        }
      } ~ path("get-members") {
        parameters("groupname") { (groupname: String) =>
          complete(authDB.getMembers(groupname))
        }
      }
    }
  }
}
