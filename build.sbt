import Dependencies._
lazy val root = (project in file(".")).
  settings(
    version         := "1.0.0",
    organization    := "org.aalto.asia",
    scalaVersion    := "2.12.6" ,
    name            := "O-MI-Authorization",
    maintainer := "Tuomas Kinnunen <tuomas.kinnunen@aalto.fi>; Lauri Isojärvi <lauri.isojarvi@aalto.fi>",
    libraryDependencies ++= akka_dependencies,
    libraryDependencies ++= akka_test_dependencies,
    libraryDependencies ++= scala_test_dependencies,
    libraryDependencies ++= slick_dependencies,
    parallelExecution in Test := false,
    scalacOptions := Seq("-unchecked", "-feature", "-deprecation", "-encoding", "utf8", "-Xlint"),
    scalacOptions in Test ++= Seq("-Yrangepos","-unchecked", "-feature", "-deprecation", "-encoding", "utf8", "-Xlint"),
    bashScriptExtraDefines += """addJava "-Dconfig.file=${app_home}/../conf/application.conf"""",
    bashScriptExtraDefines += """addJava "-Dlogback.configurationFile=${app_home}/../conf/logback.xml"""",
    bashScriptExtraDefines += """cd  ${app_home}/..""",
    batScriptExtraDefines += """call :add_java "-Dconfig.file=%APP_HOME%\\conf\\application.conf"""",
    batScriptExtraDefines += """call :add_java "-Dlogback.configurationFile=%APP_HOME%\\conf\\logback.xml"""",
    batScriptExtraDefines += """cd "%~dp0\.."""",

    mappings in Universal ++= {
      val src = sourceDirectory.value
      val conf = src / "main" / "resources" 
      Seq(
        conf / "reference.conf" -> "conf/application.conf",
        conf / "logback.xml" -> "conf/logback.xml")
    },
    mappings in Universal ++= {
      val base = baseDirectory.value
      Seq(
        base / "README.md" -> "README.md")
    }
  )
enablePlugins(JavaServerAppPackaging, SystemdPlugin)
enablePlugins(UniversalPlugin)
enablePlugins(LinuxPlugin)

