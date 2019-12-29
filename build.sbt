name := "green-tunnel-scala"

version := "1.1"

scalaVersion := "2.13.1"

lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization := "com.github.alikemalocalan",
      scalaVersion := scalaVersion.toString
    )),
    name := "grenn-tunnel-scala",
    libraryDependencies ++= Seq(
      "com.typesafe" % "config" % "1.4.0",
      "ch.qos.logback" % "logback-classic" % "1.2.3",
      "com.squareup.okhttp3" % "okhttp" % "4.2.2",

      "org.json4s" %% "json4s-native" % "3.7.0-M1",
      "com.github.jgonian" % "commons-ip-math" % "1.32",

      "io.netty" % "netty-all" % "4.1.44.Final"
    )
  )

mainClass in Compile := Some("com.github.alikemalocalan.tunnel.HttpProxyServer")

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

parallelExecution in Test := false

fork := true