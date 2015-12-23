name := """airbnb-scrap"""

version := "1.0"

val scalaVersionStr = "2.11.7"
scalaVersion := scalaVersionStr

val akkaVersion = "2.4.1"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion,
  "com.typesafe.akka" % "akka-http-experimental_2.11" % "2.0-M2",
  "com.typesafe.akka" % "akka-http-spray-json-experimental_2.11" % "2.0-M2",
  "com.typesafe.akka" % "akka-typed-experimental_2.11" % "2.4-M2",
  "com.github.scopt" %% "scopt" % "3.3.0",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0",
  "org.jsoup" % "jsoup" % "1.8.3",
  "ch.qos.logback" % "logback-classic" % "1.1.3",
  "org.scalatest" %% "scalatest" % "2.2.4" % "test",
  "junit" % "junit" % "4.12" % "test",
  "com.novocode" % "junit-interface" % "0.11" % "test"
)

dependencyOverrides ++= Set(
  "org.scala-lang" % "scala-reflect" % scalaVersionStr,
  "org.scala-lang.modules" %% "scala-xml" % "1.0.4"
)

mainClass in assembly := Some("brahms5.ScraperCliMain")
assemblyJarName in assembly := "airbnb-scrape.jar"

fork in run := true