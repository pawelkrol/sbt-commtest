isSnapshot := false

libraryDependencies += "commons-io" % "commons-io" % "2.6"

maxErrors := 1

name := "sbt-commtest"

organization := "com.github.pawelkrol"

sbtPlugin := true

scalaVersion := "2.10.6"

version := "0.02"

publishTo <<= version { v: String =>
  val nexus = "https://oss.sonatype.org/"
  if (v.trim.endsWith("SNAPSHOT"))
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
      <name>Scala License</name>
      <url>http://www.scala-lang.org/license/</url>
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