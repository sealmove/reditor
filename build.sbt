name := """reditor"""
organization := "com.example"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.13.6"

libraryDependencies ++= Seq(
  guice,
  jdbc,
  evolutions,
  "org.postgresql" % "postgresql" % "42.2.23",
  "org.playframework.anorm" %% "anorm" % "2.6.10"
)