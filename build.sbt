name := "Leon"

version := "2.0"

organization := "ch.epfl.lara"

scalaVersion := "2.9.2"

scalacOptions += "-deprecation"

scalacOptions += "-unchecked"

javacOptions += "-Xlint:unchecked"

libraryDependencies += "org.scala-lang" % "scala-compiler" % "2.9.2"

libraryDependencies += "org.scalatest" %% "scalatest" % "1.8" % "test"

if(System.getProperty("sun.arch.data.model") == "64") {
  unmanagedBase <<= baseDirectory { base => base / "unmanaged" / "64" }
} else {
  unmanagedBase <<= baseDirectory { base => base / "unmanaged" / "32" }
}

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

libraryDependencies += "com.typesafe.akka" % "akka-actor" % "2.0.4"

fork in run := true

fork in test := true

mainClass in (Compile, run) := Some("leon.Main")
