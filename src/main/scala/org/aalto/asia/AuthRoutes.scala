package org.aalto.asia

import akka.actor.{ ActorRef, ActorSystem }
import akka.event.Logging

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

  implicit def system: ActorSystem
  val authDB: AuthorizationDB

  lazy val log = Logging(system, classOf[AuthRoutes])

  // Required by the `ask` (?) method below
  implicit lazy val timeout = Timeout(5.seconds) // usually we'd obtain the timeout from the system's configuration

  //TODO: rename path
  lazy val authRoute = path("auth") {
    post {
      entity(as[PermissionRequest]) { pr =>
        val permissions: Future[PermissionResult] = authDB.userRulesForRequest(pr.username, Request(pr.requestType))
        complete(permissions)
      }
    }
  }

  lazy val mngRoute = path("mng") {
    post {
      entity(as[AddUser]) { ar: AddUser =>
        val result: Future[Int] = authDB.newUser(ar.username)
        complete(result)
      } ~ entity(as[AddGroup]) { ar: AddGroup =>
        val result: Future[Int] = authDB.newGroup(ar.groupname)
        complete(result)
      } ~ entity(as[JoinGroup]) { ar: JoinGroup =>
        val result: Future[Option[Int]] = authDB.joinGroup(ar.username, ar.groupname)
        complete(result)
      } ~ entity(as[AddRules]) { ar: AddRules =>
        //TODO: Rething AddRule format: (path, request, allow) tuples?
        val result: Future[Option[Int]] = authDB.newRulesForPaths(ar.group, ar.request, ar.allow, ar.paths)
        complete(result)
      }
    }
  }

  val routes = mngRoute ~ authRoute
}
