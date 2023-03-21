import sbt._

object Versions {
  val akkaVersion = "2.6.18"
  val scalaTestVersion = "3.2.9"
  val logbackVersion = "1.2.10"
}

object Dependencies {

  private val akka = Seq(
    "com.typesafe.akka" %% "akka-actor-typed" % Versions.akkaVersion,
    "com.typesafe.akka" %% "akka-actor-testkit-typed" % Versions.akkaVersion
  )

  private val test = Seq(
    "org.scalatest" %% "scalatest" % Versions.scalaTestVersion
  )

  private val logback = Seq(
    "ch.qos.logback" % "logback-classic" % Versions.logbackVersion
  )

  val appDependencies = akka ++ test ++ logback

}
