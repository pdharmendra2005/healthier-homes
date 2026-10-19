name := "healthier-homes"
version := "0.1.0-SNAPSHOT"
scalaVersion := "2.13.15"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

libraryDependencies ++= Seq(
  guice,
  "org.scalatestplus.play" %% "scalatestplus-play" % "7.0.1" % Test
)

TwirlKeys.templateImports += "models._"
