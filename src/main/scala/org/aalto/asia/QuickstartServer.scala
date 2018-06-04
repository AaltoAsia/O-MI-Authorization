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
import org.json4s.native.Serialization.write
import database._

object QuickstartServer extends App with AuthRoutes with JsonSupport {

  //implicit val formats = Serialization.formats(NoTypeHints) + new PathSerializer + new RequestSerializer
  val addUser = AddUser("Tester1")
  val addGroup = AddGroup("Testers")
  val joinGroups = JoinGroups("Tester1", Set("Testers"))
  val setDefault = SetRules("DEFAULT", Vector(Rule(Path("Objects/"), Read(), true), Rule(Path("Objects/"), WriteCallDelete(), false)))
  val leaveGroups = LeaveGroups("Tester1", Set("Testers"))
  val removeUser = RemoveUser("Tester1")
  val removeGroup = RemoveGroup("Testers")

  println("API JSON STRUCTURES:")
  println(write(addUser))
  println(write(addGroup))
  println(write(joinGroups))
  println(write(setDefault))
  println(write(leaveGroups))
  println(write(removeUser))
  println(write(removeGroup))
  println()
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
