
import sbt._
import Keys._

object Dependencies {
  lazy val akkaHttpVersion = "10.1.1"
  lazy val akkaVersion    = "2.5.12"
  val akka_dependencies= Seq(
    "com.typesafe.akka" %% "akka-http"            % akkaHttpVersion,
    //"com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
    "com.typesafe.akka" %% "akka-http-xml"        % akkaHttpVersion,
    "com.typesafe.akka" %% "akka-stream"          % akkaVersion,
    "com.typesafe.akka" %% "akka-actor" % akkaVersion,
    "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
    "com.typesafe.akka" %% "akka-http-core" % akkaHttpVersion
  )
  val akka_test_dependencies= Seq(
    "com.typesafe.akka" %% "akka-http-testkit"    % akkaHttpVersion % Test,
    "com.typesafe.akka" %% "akka-testkit"         % akkaVersion     % Test,
    "com.typesafe.akka" %% "akka-stream-testkit"  % akkaVersion     % Test
  )
  val scala_test_dependencies = Seq(
    "org.scalatest"     %% "scalatest"            % "3.0.1"         % Test
  )

  //etc
  val logback          = "ch.qos.logback" % "logback-classic" % "1.1.3"

  val slickV = "3.2.3"
  val h2           = "com.h2database"      % "h2"             % "1.4.192" //common
  val postgres     = "org.postgresql"      % "postgresql"      % "9.4.1211"

    val json4s       = "org.json4s"         %% "json4s-native"   % "3.5.3" //common
    val json4sAkka = "de.heikoseeberger" %% "akka-http-json4s" % "1.16.0" //common
  val slick_dependencies= Seq(
    "com.typesafe.slick" %% "slick" % slickV,
    "com.typesafe.slick" %% "slick-codegen"  % slickV,
    "com.typesafe.slick" %% "slick-hikaricp" % slickV,
    h2,
    postgres,
    json4s,
    json4sAkka
  )
}
