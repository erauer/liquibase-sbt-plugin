import S3._

sbtPlugin := true

name := "liquibase-sbt-plugin"

version := "0.1.0"

scalaVersion := "2.9.2"

organization := "com.github.sdb"


libraryDependencies ++= Seq(
	"org.liquibase" % "liquibase-core" % "2.0.5"
)

scalacOptions ++= Seq("-unchecked", "-deprecation")

publishArtifact in (Compile, packageBin) := true

publishArtifact in (Test, packageBin) := false

publishArtifact in (Compile, packageDoc) := false

publishArtifact in (Compile, packageSrc) := false

