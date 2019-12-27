name := "green-tunnel-scala"

version := "0.1"

scalaVersion := "2.13.1"



lazy val scalaTestV = "3.1.0"

lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization    := "com.github.alikemalocalan",
      scalaVersion    := scalaVersion.toString
    )),
    name := "hello-world",
    libraryDependencies ++= Seq(
      "com.typesafe" % "config" % "1.4.0",
      "ch.qos.logback" % "logback-classic" % "1.2.3",
      "org.scalatest" %% "scalatest" % scalaTestV % Test,
      "com.athaydes.rawhttp" % "rawhttp-core" % "2.2.1",
      "commons-io" % "commons-io" % "2.6",
      "com.lambdista" % "try" % "0.3.1",
      "com.squareup.okhttp3" % "okhttp" % "3.13.1",

      "org.json4s" %% "json4s-native" % "3.7.0-M1",
      "org.json4s" %% "json4s-jackson" % "3.7.0-M1",
      "com.github.jgonian" % "commons-ip-math" % "1.32",

      "io.netty" % "netty-all" % "4.1.44.Final",
      "org.slf4j" % "log4j-over-slf4j" % "1.7.30"

    )
  )

mainClass in Compile := Some("com.github.alikemalocalan.Proxy")

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

parallelExecution in Test := false

fork := true