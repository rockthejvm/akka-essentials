package part3testing

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import org.scalatest.wordspec.AnyWordSpecLike
import scala.concurrent.duration._

import scala.util.Random

class TimedAssertionsSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  import TimedAssertionsSpec._

  "A worker actor" should {
    val worker = testKit.spawn(WorkerActor())
    val probe = testKit.createTestProbe[ResultMessage]()

    "reply with a work result in at most 1 second" in {
      worker ! Work(probe.ref)
      probe.expectMessage(1.second, WorkResult(42))
    }

    "reply with the meaning of life in at least half a second" in {
      worker ! Work(probe.ref)
      probe.expectNoMessage(500.millis)
      probe.expectMessage(500.millis, WorkResult(42))
    }

    "v2: reply with the meaning of life in between 0.5 and 1s" in {
      worker ! Work(probe.ref)

      probe.within(500.millis, 1.second) {
        // scenario, run as many assertions as you like
        probe.expectMessage(WorkResult(42))
      }
    }

    "reply with multiple results in a timely manner" in {
      worker ! WorkSequence(probe.ref)
      val results = probe.receiveMessages(10, 1.second)

      results
        .collect {
          case WorkResult(value) => value
        }
        .sum should be > 5
    }

  }
}

object TimedAssertionsSpec {
  trait ResultMessage
  case class WorkResult(result: Int) extends ResultMessage

  trait Message
  case class Work(replyTo: ActorRef[ResultMessage]) extends Message
  case class WorkSequence(replyTo: ActorRef[ResultMessage]) extends Message

  object WorkerActor {
    def apply(): Behavior[Message] = Behaviors.receiveMessage {
      case Work(replyTo) =>
        // wait a bit - simulate a long computation
        Thread.sleep(500)
        replyTo ! WorkResult(42)
        Behaviors.same
      case WorkSequence(replyTo) =>
        val random = new Random()
        (1 to 10).foreach { _ =>
          Thread.sleep(random.nextInt(50))
          replyTo ! WorkResult(1)
        }
        Behaviors.same

    }
  }
}
