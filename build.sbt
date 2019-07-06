isSnapshot := false

libraryDependencies ++= Seq(
  "commons-io" % "commons-io" % "2.6"
)

maxErrors := 1

name := "sbt-commtest"

organization := "com.github.pawelkrol"

sbtPlugin := true

scalaVersion := "2.12.8"

version := "0.05-SNAPSHOT"

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases" at nexus + "service/local/staging/deploy/maven2")
}

publishMavenStyle := true

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

pomExtra := (
  <url>https://github.com/pawelkrol/sbt-commtest</url>
  <licenses>
    <license>
      <name>Apache License, Version 2.0</name>
      <url>https://www.apache.org/licenses/LICENSE-2.0</url>
      <distribution>repo</distribution>
    </license>
  </licenses>
  <scm>
    <url>git://github.com/pawelkrol/sbt-commtest</url>
    <connection>scm:git:git://github.com/pawelkrol/sbt-commtest.git</connection>
  </scm>
  <developers>
    <developer>
      <id>pawelkrol</id>
      <name>Pawel Krol</name>
      <url>https://github.com/pawelkrol/sbt-commtest</url>
    </developer>
  </developers>
)
