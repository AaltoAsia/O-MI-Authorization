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

  implicit val formats = Serialization.formats(NoTypeHints) //+ new PathSerializer
  val addUser = AddUser("Tester1")
  val addGroup = AddGroup("Testers")
  val joinGroups = JoinGroups("Tester1", Set("Testers"))
  val setDefault = SetRules("DEFAULT", Vector(Rule(Path("Objects/"), Read(), true), Rule(Path("Objects/"), WriteCallDelete(), false)))
  val leaveGroups = LeaveGroups("Tester1", Set("Testers"))
  val removeUser = RemoveUser("Tester1")
  val removeGroup = RemoveGroup("Testers")

  println(write(addUser)(formats))
  println(write(addGroup)(formats))
  println(write(joinGroups)(formats))
  println(write(setDefault)(formats))
  println(write(leaveGroups)(formats))
  println(write(removeUser)(formats))
  println(write(removeGroup)(formats))
  // set up ActorSystem and other dependencies here
  implicit val system: ActorSystem = ActorSystem("O-MI-Authorization-Server")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val authDB = new AuthorizationDB()

  Http().bindAndHandle(routes, "localhost", 8080)
  println(s"Server online at http://localhost:8080/")

  Await.result(system.whenTerminated, Duration.Inf)
}
