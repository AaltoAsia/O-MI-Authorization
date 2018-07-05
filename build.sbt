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
    libraryDependencies ++= slick_dependencies,
    parallelExecution in Test := false,
    scalacOptions := Seq("-unchecked", "-feature", "-deprecation", "-encoding", "utf8", "-Xlint"),
    scalacOptions in Test ++= Seq("-Yrangepos","-unchecked", "-feature", "-deprecation", "-encoding", "utf8", "-Xlint")
  )
enablePlugins(JavaServerAppPackaging)
enablePlugins(UniversalPlugin)
enablePlugins(LinuxPlugin)

