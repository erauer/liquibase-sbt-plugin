sbtPlugin := true

name := "liquibase-sbt-plugin"

version := "0.0.6-SNAPSHOT"

organization := "com.github.sdb"


libraryDependencies ++= Seq(
	"org.liquibase" % "liquibase-core" % "2.0-rc6"
)

scalacOptions ++= Seq("-unchecked", "-deprecation")

publishArtifact in (Compile, packageBin) := true

publishArtifact in (Test, packageBin) := false

publishArtifact in (Compile, packageDoc) := false

publishArtifact in (Compile, packageSrc) := false

