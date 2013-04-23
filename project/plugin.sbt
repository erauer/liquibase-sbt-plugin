resolvers ++= Seq(
  Classpaths.typesafeSnapshots,
 "Web plugin repo" at "http://siasia.github.com/maven2",
 "less is" at "http://repo.lessis.me"
)

addSbtPlugin("com.typesafe.sbt" % "sbt-s3" % "0.4")

resolvers += Classpaths.typesafeResolver
