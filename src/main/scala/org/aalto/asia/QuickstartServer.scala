package org.aalto.asia

//#quick-start-server
import scala.concurrent.Await
import scala.concurrent.duration.Duration

import akka.actor.{ ActorRef, ActorSystem }
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import org.aalto.asia.requests._
import org.aalto.asia.types._
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.native.Serialization
import org.json4s.native.Serialization.{ write, read }
import database._

object QuickstartServer extends App with AuthRoutes with JsonSupport {

  // set up ActorSystem and other dependencies here
  implicit val system: ActorSystem = ActorSystem("O-MI-Authorization-Server")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val authDB = new AuthorizationDB()

  val interface = "localhost"
  val port = 8001
  Http().bindAndHandle(routes, interface, port)
  println(s"Server online at http://$interface:$port/")

  Await.result(system.whenTerminated, Duration.Inf)
}
