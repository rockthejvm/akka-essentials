package part5patterns

import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors

import java.util.concurrent.Executors
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import utils._
import scala.concurrent.duration._

object PipeDemo {

  // interaction with an external service that returns Futures
  val db: Map[String, Int] = Map(
    "Daniel" -> 123,
    "Jane" -> 456,
    "Dee Dee" -> 999
  )

  val executor = Executors.newFixedThreadPool(4)
  implicit val externalEC: ExecutionContext = ExecutionContext.fromExecutorService(executor) // for running the external service

  def callExternalService(name: String): Future[Int] = {
    // select phoneNo from people where ...
    Future(db(name))
  }

  trait PhoneCallProtocol
  case class FindAndCallPhoneNumber(name: String) extends PhoneCallProtocol
  case class InitiatePhoneCall(number: Int) extends PhoneCallProtocol
  case class LogPhoneCallFailure(reason: Throwable) extends PhoneCallProtocol

  object PhoneCallActor {
    def apply(): Behavior[PhoneCallProtocol] = Behaviors.receive { (context, message) =>
      message match {
        case FindAndCallPhoneNumber(name) =>
          context.log.info(s"Fetching the phone number for $name")
          // pipe pattern
          // 1 - have the Future ready
          val phoneNumberFuture = callExternalService(name)
          // 2 - pipe the Future result back to me as a message
          context.pipeToSelf(phoneNumberFuture) {
            case Success(number) => InitiatePhoneCall(number)
            case Failure(ex) => LogPhoneCallFailure(ex)
          }
          Behaviors.same
        case InitiatePhoneCall(number) =>
          // perform the phone call
          context.log.info(s"Initiating phone call to $number")
          Behaviors.same
        case LogPhoneCallFailure(reason) =>
          context.log.warn(s"Initiating phone call failed: $reason")
          Behaviors.same
      }
    }
  }

  def main(args: Array[String]): Unit = {
    val userGuardian = Behaviors.setup[Unit] { context =>
      val phoneCallActor = context.spawn(PhoneCallActor(), "phoneCallActor")

      phoneCallActor ! FindAndCallPhoneNumber("Superman")

      Behaviors.empty
    }

    ActorSystem(userGuardian, "DemoPipePattern").withFiniteLifespan(2.seconds)
  }
}
