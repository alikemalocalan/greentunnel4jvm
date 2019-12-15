name := "green-tunnel-scala"

version := "0.1"

scalaVersion := "2.13.1"


lazy val akkaHttpV = "10.1.11"
lazy val akkaV = "2.6.1"
lazy val scalaTestV = "3.1.0"

lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization    := "com.github.alikemalocalan",
      scalaVersion    := scalaVersion.toString
    )),
    name := "hello-world",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpV ,
      "com.typesafe.akka" %% "akka-stream" % akkaV,
      "com.typesafe.akka" %% "akka-slf4j" % akkaV,
      "org.scalaz" %% "scalaz-core" % "7.2.27",
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2",
      "ch.qos.logback" % "logback-classic" % "1.2.3",
      "org.scalatest" %% "scalatest" % scalaTestV % Test,
      "com.athaydes.rawhttp" % "rawhttp-core" % "2.2.1",
      "commons-io" % "commons-io" % "2.6"

    )
  )

mainClass in Compile := Some("com.github.alikemalocalan.Proxy")

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

parallelExecution in Test := false

fork := true