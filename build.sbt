
name := "ImageJVolFilePlugin"

version := "1.0"

scalaVersion := "2.11.11"

resourceDirectory in Compile := baseDirectory.value / "resources"

lazy val root = (project in file(".")).
  enablePlugins(AssemblyPlugin)

libraryDependencies ++= Seq(
  "net.imagej" % "ij" % "1.51n",
  //"ome" % "bioformats_package" % "5.5.2",
  "ch.nanolive" %% "javavolfileapi" % "1.0.0" withSources()
)


resolvers  ++= Seq(
  "Nanolive Repository" at "https://artifactory.nanolive.ch/artifactory/sbt-release/",
  "imagej.public" at "http://maven.imagej.net/content/groups/public",
  "Open microscopy" at "http://artifacts.openmicroscopy.org/artifactory/ome.releases",
  "Typesafe Repo" at "http://repo.typesafe.com/typesafe/releases/"
)

assemblyExcludedJars in assembly := {
  val cp = (fullClasspath in assembly).value
  cp filter {_.data.getName == "ij-1.51n.jar"}
}

assemblyJarName := "Nanolive_Volfile_Reader.jar"
