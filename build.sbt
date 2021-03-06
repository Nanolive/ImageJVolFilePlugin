
name := "ImageJVolFilePlugin"

version := "1.0"

scalaVersion := "2.11.7"

resourceDirectory in Compile := baseDirectory.value / "resources"

lazy val root = (project in file(".")).
  enablePlugins(AssemblyPlugin)

libraryDependencies ++= Seq(
  "net.imagej" % "ij" % "1.51n",
  "javax.media" % "jai_codec" % "1.1.3",
  "ch.nanolive" %% "javavolfileapi" % "1.0.26" withSources()
)


resolvers  ++= Seq(
  "Nanolive Repository" at "https://artifactory.nanolive.ch/artifactory/sbt-release/",
  "imagej.public" at "http://maven.imagej.net/content/groups/public",
  "Typesafe Repo" at "http://repo.typesafe.com/typesafe/releases/"
)

val ignoredJars = Set(
  "ij-1.51n.jar",
  "scala-library-2.12.2.jar",
  "scala-reflect-2.12.2.jar"
)

assemblyExcludedJars in assembly := {
  val cp = (fullClasspath in assembly).value
  cp filter {jar => ignoredJars.contains(jar.data.getName)}
}

assemblyJarName := "Nanolive_Volfile_Reader.jar"
