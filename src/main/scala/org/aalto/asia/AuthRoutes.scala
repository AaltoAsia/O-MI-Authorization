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

trait AuthRoutes extends JsonSupport {

  implicit def system: ActorSystem
  val authDB: AuthorizationDB

  lazy val log = Logging(system, classOf[AuthRoutes])

  // Required by the `ask` (?) method below
  implicit lazy val timeout = Timeout(5.seconds) // usually we'd obtain the timeout from the system's configuration

  lazy val readRoute = path("read" / Segment) { role_name: String =>
    val permissions: Future[PermissionResult] = authDB.userRulesForRequest(Set(role_name), Read())
    complete(permissions)
  }

  //TODO: rename path
  lazy val jsRoute = path("auth") {
    post {
      entity(as[PermissionRequest]) { pr =>
        val permissions: Future[PermissionResult] = authDB.userRulesForRequest(pr.roles, Request(pr.requestType))
        complete(permissions)
      }
    }
  }

  /*
  lazy val mngRoute = path("mng") {
    post {
      entity(as[AddRole]){ ar =>
      }
    }
  }*/

  lazy val writeRoute = path("write" / Segment) { role_name: String =>
    val permissions: Future[PermissionResult] = authDB.userRulesForRequest(Set(role_name), Write())
    complete(permissions)
  }
  val routes = get(concat(readRoute, writeRoute))
}
