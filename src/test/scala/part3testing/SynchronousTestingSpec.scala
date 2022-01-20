package part3testing

import akka.actor.testkit.typed.CapturedLogEvent
import akka.actor.testkit.typed.Effect.Spawned
import akka.actor.testkit.typed.scaladsl.{BehaviorTestKit, ScalaTestWithActorTestKit, TestInbox}
import org.scalatest.wordspec.AnyWordSpecLike
import org.slf4j.event.Level
import part2actors.ChildActorsExercise._

class SynchronousTestingSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  "A word counter master" should {
    "spawn a child upon reception of the initialize message" in {
      val master = BehaviorTestKit(WordCounterMaster())
      master.run(Initialize(1)) // synchronous "sending" of the message Initialize(1)

      // check that an effect was produced
      val effect = master.expectEffectType[Spawned[WorkerProtocol]]
      // inspect the contents of those effects
      effect.childName should equal("worker1")
    }

    "send a task to a child" in {
      val master = BehaviorTestKit(WordCounterMaster())
      master.run(Initialize(1))

      // from the previous test - "consume" the event
      val effect = master.expectEffectType[Spawned[WorkerProtocol]]

      val mailbox = TestInbox[UserProtocol]() // the "requester"'s inbox
      // start processing
      master.run(WordCountTask("Akka testing is pretty powerful!", mailbox.ref))
      // mock the reply from the child
      master.run(WordCountReply(0, 5))
      // test that the requester got the right message
      mailbox.expectMessage(Reply(5))
    }

    "log messages" in {
      val master = BehaviorTestKit(WordCounterMaster())
      master.run(Initialize(1))
      master.logEntries() shouldBe Seq(CapturedLogEvent(Level.INFO, "[master] initializing with 1 children"))
    }
  }
}
