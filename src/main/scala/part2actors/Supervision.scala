package part2actors

import akka.actor.typed.{ActorSystem, Behavior, SupervisorStrategy, Terminated}
import akka.actor.typed.scaladsl.Behaviors
import scala.concurrent.duration._

object Supervision {

  object FussyWordCounter {
    def apply(): Behavior[String] = active()

    def active(total: Int = 0): Behavior[String] = Behaviors.receive { (context, message) =>
      val wordCount = message.split(" ").length
      context.log.info(s"Received piece of text: '$message', counted $wordCount words, total: ${total + wordCount}")
      // throw some exceptions (maybe unintentionally)
      if (message.startsWith("Q")) throw new RuntimeException("I HATE queues!")
      if (message.startsWith("W")) throw new NullPointerException

      active(total + wordCount)
    }
  }

  // actor throwing exception gets killed

  def demoCrash(): Unit = {
    val guardian: Behavior[Unit] = Behaviors.setup { context =>
      val fussyCounter = context.spawn(FussyWordCounter(), "fussyCounter")

      fussyCounter ! "Starting to understand this Akka business..."
      fussyCounter ! "Quick! Hide!"
      fussyCounter ! "Are you there?"

      Behaviors.empty
    }

    val system = ActorSystem(guardian, "DemoCrash")
    Thread.sleep(1000)
    system.terminate()
  }

  def demoWithParent(): Unit = {
    val parentBehavior: Behavior[String] = Behaviors.setup { context =>
      val child = context.spawn(FussyWordCounter(), "fussyChild")
      context.watch(child)

      Behaviors.receiveMessage[String] { message =>
        child ! message
        Behaviors.same
      }
      .receiveSignal {
        case (context, Terminated(childRef)) =>
          context.log.warn(s"Child failed: ${childRef.path.name}")
          Behaviors.same
      }
    }

    val guardian: Behavior[Unit] = Behaviors.setup { context =>
      val fussyCounter = context.spawn(parentBehavior, "fussyCounter")

      fussyCounter ! "Starting to understand this Akka business..."
      fussyCounter ! "Quick! Hide!"
      fussyCounter ! "Are you there?"

      Behaviors.empty
    }

    val system = ActorSystem(guardian, "DemoCrashWithParent")
    Thread.sleep(1000)
    system.terminate()
  }

  def demoSupervisionWithRestart(): Unit = {
    val parentBehavior: Behavior[String] = Behaviors.setup { context =>
      // supervise the child with a restart "strategy"
      val childBehavior = Behaviors.supervise(
        Behaviors.supervise(FussyWordCounter())
        .onFailure[RuntimeException](SupervisorStrategy.restart)
      ).onFailure[NullPointerException](SupervisorStrategy.resume)

      val child = context.spawn(childBehavior, "fussyChild")
      context.watch(child)

      Behaviors.receiveMessage[String] { message =>
        child ! message
        Behaviors.same
      }
      .receiveSignal {
        case (context, Terminated(childRef)) =>
          context.log.warn(s"Child failed: ${childRef.path.name}")
          Behaviors.same
      }
    }

    val guardian: Behavior[Unit] = Behaviors.setup { context =>
      val fussyCounter = context.spawn(parentBehavior, "fussyCounter")

      fussyCounter ! "Starting to understand this Akka business..."
      fussyCounter ! "Quick! Hide!"
      fussyCounter ! "Are you there?"
      fussyCounter ! "What are you doing?"
      fussyCounter ! "Are you still there?"

      Behaviors.empty
    }

    val system = ActorSystem(guardian, "DemoCrashWithParent")
    Thread.sleep(1000)
    system.terminate()
  }

  /**
   * Exercise: how do we specify different supervisor strategies for different exception types?
   */
  val differentStrategies = Behaviors.supervise(
    Behaviors.supervise(FussyWordCounter())
    .onFailure[NullPointerException](SupervisorStrategy.resume)
  )
    .onFailure[IndexOutOfBoundsException](SupervisorStrategy.restart)

  /*
    OO equivalent:
    try { .. }
    catch {
      case NullPointerException =>
      case IndexOutOfBoundsException =>
    }

    place the most specific exception handlers INSIDE and the most general exception handlers OUTSIDE.
   */
  def main(args: Array[String]): Unit = {
    demoSupervisionWithRestart()
  }
}
