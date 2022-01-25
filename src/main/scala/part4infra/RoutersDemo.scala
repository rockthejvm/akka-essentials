package part4infra
import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import utils._
import akka.actor.typed.scaladsl.{Behaviors, Routers}

import scala.concurrent.duration._

object RoutersDemo {

  def demoPoolRouter(): Unit = {
    val workerBehavior = LoggerActor[String]()
    val poolRouter = Routers.pool(5)(workerBehavior).withBroadcastPredicate(_.length > 11) // round robin

    val userGuardian = Behaviors.setup[Unit] { context =>
      val poolActor = context.spawn(poolRouter, "pool")

      (1 to 10).foreach(i => poolActor ! s"work task $i")

      Behaviors.empty
    }

    ActorSystem(userGuardian, "DemoPoolRouter").withFiniteLifespan(2.seconds)
  }

  def demoGroupRouter(): Unit = {
    val serviceKey = ServiceKey[String]("logWorker")
    // service keys are used by a core akka module for discovering actors and fetching their Refs

    val userGuardian = Behaviors.setup[Unit] { context =>
      // in real life the workers may be created elsewhere in your code
      val workers = (1 to 5).map(i => context.spawn(LoggerActor[String](), s"worker$i"))
      // register the workers with the service key
      workers.foreach(worker => context.system.receptionist ! Receptionist.Register(serviceKey, worker))

      val groupBehavior: Behavior[String] = Routers.group(serviceKey).withRoundRobinRouting() // random by default
      val groupRouter = context.spawn(groupBehavior, "workerGroup")

      (1 to 10).foreach(i => groupRouter ! s"work task $i")

      // add new workers later
      Thread.sleep(1000)
      val extraWorker = context.spawn(LoggerActor[String](), "extraWorker")
      context.system.receptionist ! Receptionist.Register(serviceKey, extraWorker)
      (1 to 10).foreach(i => groupRouter ! s"work task $i")

      /*
        removing workers:
        - send the receptionist a Receptionist.Deregister(serviceKey, worker, someActorToReceiveConfirmation)
        - receive Receptionist.Deregistered in someActorToReceiveConfirmation, best practice, someActorToReceiveConfirmation == worker
        --- in this time, there's a risk that the router might still use the worker as the routee
        - safe to stop the worker
       */
      Behaviors.empty
    }

    ActorSystem(userGuardian, "DemoGroupRouter").withFiniteLifespan(2.seconds)
  }

  def main(args: Array[String]): Unit = {
    demoGroupRouter()
  }
}
