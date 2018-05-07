import Dependencies._

lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization    := "org.aalto.asia",
      scalaVersion    := "2.12.5"
    )),
    name := "O-MI Authorization",
    libraryDependencies ++= akka_dependencies,
    libraryDependencies ++= akka_test_dependencies,
    libraryDependencies ++= scala_test_dependencies,
    libraryDependencies ++= slick_dependencies
  )
