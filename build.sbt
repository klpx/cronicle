name := "cronicle"
organization := "ru.arigativa"

version := "1.0.1"

scalaVersion := "2.12.2"

crossScalaVersions := Seq("2.12.2", "2.11.8")

libraryDependencies ++= Seq(
  "com.github.alonsodomin.cron4s" %% "cron4s-core" % "0.4.0"
  ,"ch.qos.logback" % "logback-classic" % "1.1.6"
  ,"org.scalatest" %% "scalatest" % "3.0.1" % "test"
)
