addSbtPlugin("org.scalariform" % "sbt-scalariform" % "1.8.2")

addSbtPlugin("io.spray" % "sbt-revolver" % "0.9.1")

resolvers += "Typesafe Repository" at "https://repo.typesafe.com/typesafe/releases/"

// for autoplugins
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.3.4")
