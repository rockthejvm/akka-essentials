package part5patterns

import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import utils._
import scala.concurrent.duration._

object StashDemo {

  // an actor with a locked access to a resource
  trait Command
  case object Open extends Command
  case object Close extends Command
  case object Read extends Command
  case class Write(data: String) extends Command

  object ResourceActor {
    def apply(): Behavior[Command] = closed("42") // the resource starts as closed with some initial data

    def closed(data: String): Behavior[Command] = Behaviors.withStash(128) { buffer =>
      Behaviors.receive { (context, message) =>
        message match {
          case Open =>
            context.log.info("Opening Resource")
            buffer.unstashAll(open(data)) // open(data) is the next behavior, AFTER unstashing
          case _ =>
            context.log.info(s"Stashing $message because the resource is closed")
            buffer.stash(message) // buffer is MUTABLE
            Behaviors.same
        }
      }
    }

    def open(data: String): Behavior[Command] = Behaviors.receive { (context, message) =>
      message match {
        case Read =>
          context.log.info(s"I have read $data") // <- in real life you would fetch some actual data
          Behaviors.same
        case Write(newData) =>
          context.log.info(s"I have written $newData")
          open(newData)
        case Close =>
          context.log.info(s"Closing Resource")
          closed(data)
        case message =>
          context.log.info(s"$message not supported while resource is open")
          Behaviors.same
      }

    }
  }

  def main(args: Array[String]): Unit = {
    val userGuardian = Behaviors.setup[Unit] { context =>
      val resourceActor = context.spawn(ResourceActor(), "resource")

      resourceActor ! Read // stashed
      resourceActor ! Open // unstash the Read message after opening
      resourceActor ! Open // unhandled
      resourceActor ! Write("I love stash") // overwrite
      resourceActor ! Write("This is pretty cool") // overwrite
      resourceActor ! Read
      resourceActor ! Read
      resourceActor ! Close
      resourceActor ! Read // stashed: resource is closed

      Behaviors.empty
    }
    ActorSystem(userGuardian, "DemoStash").withFiniteLifespan(2.seconds)
  }
}
