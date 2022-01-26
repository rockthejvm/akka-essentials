package part5patterns

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, Scheduler}
import akka.util.Timeout

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}
import scala.concurrent.duration._
import utils._
import scala.util.{Try, Success, Failure}

object AskDemo {

  trait WorkProtocol
  case class ComputationalTask(payload: String, replyTo: ActorRef[WorkProtocol]) extends WorkProtocol
  case class ComputationalResult(result: Int) extends WorkProtocol

  object Worker {
    def apply(): Behavior[WorkProtocol] = Behaviors.receive { (context, message) =>
      message match {
        case ComputationalTask(text, destination) =>
          context.log.info(s"[worker] Crunching data for $text")
          destination ! ComputationalResult(text.split(" ").length)
          Behaviors.same
        case _ => Behaviors.same
      }
    }
  }

  def askSimple(): Unit = {
    // 1 - import the right package
    import akka.actor.typed.scaladsl.AskPattern._

    // 2 - set up some implicits
    val system = ActorSystem(Worker(), "DemoAskSimple").withFiniteLifespan(5.seconds)
    implicit val timeout: Timeout = Timeout(3.seconds)
    implicit val scheduler: Scheduler = system.scheduler

    // 3 - call the ask method
    val reply: Future[WorkProtocol] = system.ask(ref => ComputationalTask("Trying the ask pattern, seems convoluted", ref))
    //                                           ^ temporary actor   ^ message that gets sent to the worker == the user guardian

    // 4 - process the Future
    implicit val ec: ExecutionContext = system.executionContext
    reply.foreach(println)
  }

  def askFromWithinAnotherActor(): Unit = {
    val userGuardian = Behaviors.setup[WorkProtocol] { context =>
      val worker = context.spawn(Worker(), "worker")

      // 0 - define extra messages that I should handle as results of ask
      case class ExtendedComputationalResult(count: Int, description: String) extends WorkProtocol

      // 1 - set up the implicits
      implicit val timeout: Timeout = Timeout(3.seconds)

      // 2 - ask
      context.ask(worker, ref => ComputationalTask("This ask pattern seems quite complicated", ref)) {
        // Try[WorkProtocol] => WorkProtocol message that will be sent TO ME later
        case Success(ComputationalResult(count)) => ExtendedComputationalResult(count, "This is pretty damn hard")
        case Failure(ex) => ExtendedComputationalResult(-1, s"Computation failed: $ex")
      }

      // 3 - handle the result (messages) from the ask pattern
      Behaviors.receiveMessage {
        case ExtendedComputationalResult(count, description) =>
          context.log.info(s"Ask and ye shall receive: $description - $count")
          Behaviors.same
        case _ => Behaviors.same
      }
    }

    ActorSystem(userGuardian, "DemoAskConvoluted").withFiniteLifespan(5.seconds)
  }

  def main(args: Array[String]): Unit = {
    askFromWithinAnotherActor()
  }
}
