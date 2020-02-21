name := "green-tunnel-scala"

version := "1.1"

scalaVersion := "2.13.1"

val nettyVersion = "4.1.44.Final"

artifactName := { (_, _, _) =>
  s"${name.value}-${version.value}.jar"
}


lazy val root = (project in file(".")).
  settings(
    (run in Compile := Defaults.runTask(fullClasspath in Compile, mainClass in(Compile, run), runner in(Compile, run)).evaluated),
    inThisBuild(List(
      organization := "com.github.alikemalocalan",
      scalaVersion := scalaVersion.toString
    )),
    name := "grenn-tunnel-scala",
    libraryDependencies ++= Seq(
      "io.netty" % "netty-handler-proxy" % nettyVersion % Compile,

      "com.dslplatform" %% "dsl-json-scala" % "1.9.3" % Compile, // For json Parsing
      "com.typesafe" % "config" % "1.4.0" % Compile,

      "org.slf4j" % "slf4j-api" % "1.7.25" % Compile,
      "ch.qos.logback" % "logback-classic" % "1.2.3" % Test,
      "org.scalatest" %% "scalatest" % "3.1.0" % Test,

      "com.athaydes.rawhttp" % "rawhttp-core" % "2.2.1",
      "commons-io" % "commons-io" % "2.6"
    )
  )

test in assembly := {}

mainClass in Compile := Some("com.github.alikemalocalan.tunnel.HttpProxyServer")

mainClass in assembly := Some("com.github.alikemalocalan.tunnel.HttpProxyServer")

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

parallelExecution in Test := false

fork := true

assemblyMergeStrategy in assembly := {
  case PathList("META-INF", xs@_*) => MergeStrategy.discard
  case x => MergeStrategy.first
}