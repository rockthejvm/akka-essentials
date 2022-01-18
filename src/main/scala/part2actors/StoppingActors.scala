package part2actors

import akka.actor.typed.{ActorSystem, Behavior, PostStop}
import akka.actor.typed.scaladsl.Behaviors

object StoppingActors {

  object SensitiveActor {
    def apply(): Behavior[String] = Behaviors.receive[String] { (context, message) =>
      context.log.info(s"Received: $message")
      if (message == "you're ugly")
        Behaviors.stopped // optionally pass a () => Unit to clear up resources after the actor is stopped
      else
        Behaviors.same
    }
      .receiveSignal {
        case (context, PostStop) =>
          // clean up resources that this actor might use
          context.log.info("I'm stopping now.")
          Behaviors.same // not used anymore in case of stopping
      }
  }

  def main(args: Array[String]): Unit = {
    val userGuardian = Behaviors.setup[Unit] { context =>
      val sensitiveActor = context.spawn(SensitiveActor(), "sensitiveActor")

      sensitiveActor ! "Hi"
      sensitiveActor ! "How are you"
      sensitiveActor ! "you're ugly"
      sensitiveActor ! "sorry about that"

      Behaviors.empty
    }

    val system = ActorSystem(userGuardian, "DemoStoppingActor")
    Thread.sleep(1000)
    system.terminate()
  }
}
