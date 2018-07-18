package org.aalto.asia

//#quick-start-server
import scala.concurrent.Await
import scala.concurrent.duration.Duration

import akka.actor.{ ActorSystem }
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import database._

object QuickstartServer extends App with AuthRoutes with JsonSupport {

  // set up ActorSystem and other dependencies here
  implicit val system: ActorSystem = ActorSystem("O-MI-Authorization-Server")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val authDB = new AuthorizationDB()
  implicit val settings = Settings(system)
  import settings._

  Http().bindAndHandle(routes, interface, port)
  println(s"Server online at http://$interface:$port/")

  Await.result(system.whenTerminated, Duration.Inf)
}
