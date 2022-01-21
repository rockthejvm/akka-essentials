package part4infra

import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.config.ConfigFactory

object AkkaConfiguration {

  object SimpleLoggingActor {
    def apply(): Behavior[String] = Behaviors.receive { (context, message) =>
      context.log.info(message)
      Behaviors.same
    }
  }

  // 1 - inline configuration
  def demoInlineConfig(): Unit = {
    // HOCON, superset of JSON, managed by Lightbend
    val configString: String =
      """
        |akka {
        |  loglevel = "DEBUG"
        |}
        |""".stripMargin
    val config = ConfigFactory.parseString(configString)
    val system = ActorSystem(SimpleLoggingActor(), "ConfigDemo", ConfigFactory.load(config))

    system ! "A message to remember"

    Thread.sleep(1000)
    system.terminate()
  }

  // 2 - config file
  def demoConfigFile(): Unit = {
    val specialConfig = ConfigFactory.load().getConfig("mySpecialConfig")
    val system = ActorSystem(SimpleLoggingActor(), "ConfigDemo", specialConfig)

    system ! "A message to remember"

    Thread.sleep(1000)
    system.terminate()
  }

  // 3 - a different config in another file
  def demoSeparateConfigFile(): Unit = {
    val separateConfig = ConfigFactory.load("secretDir/secretConfiguration.conf")
    println(separateConfig.getString("akka.loglevel"))
  }

  // 4 - different file formats (JSON, properties)
  def demoOtherFileFormats(): Unit = {
    val jsonConfig = ConfigFactory.load("json/jsonConfiguration.json")
    println(s"json config with custom property: ${jsonConfig.getString("aJsonProperty")}")
    println(s"json config with Akka property: ${jsonConfig.getString("akka.loglevel")}")

    // properties format
    val propsConfig = ConfigFactory.load("properties/propsConfiguration.properties")
    println(s"properties config with custom property: ${propsConfig.getString("mySimpleProperty")}")
    println(s"properties config with Akka property: ${propsConfig.getString("akka.loglevel")}")
  }

  def main(args: Array[String]): Unit = {
    demoOtherFileFormats()
  }
}
