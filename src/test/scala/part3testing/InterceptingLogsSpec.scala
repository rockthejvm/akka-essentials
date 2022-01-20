package part3testing

import akka.actor.testkit.typed.scaladsl.{LoggingTestKit, ScalaTestWithActorTestKit}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import org.scalatest.wordspec.AnyWordSpecLike

class InterceptingLogsSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  import InterceptingLogsSpec._

  val item = "Rock the JVM Akka course"
  val creditCard = "1234-1234-1234-1234" // valid card
  val invalidCard = "0000-0000-0000-0000"

  "A checkout flow" should {
    "correctly dispatch an order for a valid credit card" in {
      LoggingTestKit.info("Order") // filter log messages of level INFO which contain the string "Order"
        .withMessageRegex(s"Order [0-9]+ for item $item has been dispatched.")
        .withOccurrences(1)
        .expect { // scenario under test
          val checkoutActor = testKit.spawn(CheckoutActor())
          checkoutActor ! Checkout(item, creditCard)
        }
    }

    "freak out if the payment is declined" in {
      LoggingTestKit.error[RuntimeException]
        .withOccurrences(1)
        .expect {
          val checkoutActor = testKit.spawn(CheckoutActor())
          checkoutActor ! Checkout(item, invalidCard)
        }
    }
  }
}

object InterceptingLogsSpec {

  // payment system
  /*
    checkout actor -> payment manager
                   <-

                   -> fulfillment manager
                   <-
   */
  trait PaymentProtocol
  case class Checkout(item: String, creditCard: String) extends PaymentProtocol
  case class AuthorizeCard(creditCard: String, replyTo: ActorRef[PaymentProtocol]) extends PaymentProtocol
  case object PaymentAccepted extends PaymentProtocol
  case object PaymentDeclined extends PaymentProtocol
  case class DispatchOrder(item: String, replyTo: ActorRef[PaymentProtocol]) extends PaymentProtocol
  case object OrderConfirmed extends PaymentProtocol

  object CheckoutActor {
    def apply(): Behavior[PaymentProtocol] = Behaviors.setup { context =>
      val paymentManager = context.spawn(PaymentManager(), "paymentManager")
      val fulfillmentManager = context.spawn(FulfillmentManager(), "fulfillmentManager")

      def awaitingCheckout(): Behavior[PaymentProtocol] = Behaviors.receiveMessage {
        case Checkout(item, card) =>
          context.log.info(s"Received order for item $item")
          paymentManager ! AuthorizeCard(card, context.self)
          pendingPayment(item)
        case _ => Behaviors.same
      }

      def pendingPayment(item: String): Behavior[PaymentProtocol] = Behaviors.receiveMessage {
        case PaymentAccepted =>
          fulfillmentManager ! DispatchOrder(item, context.self)
          pendingDispatch(item)
        case PaymentDeclined =>
          throw new RuntimeException("Cannot handle invalid payments") // by design
      }

      def pendingDispatch(item: String): Behavior[PaymentProtocol] = Behaviors.receiveMessage {
        case OrderConfirmed =>
          context.log.info(s"Dispatch for $item confirmed.")
          awaitingCheckout()
      }

      // the first state
      awaitingCheckout()
    }
  }

  object PaymentManager {
    def apply(): Behavior[PaymentProtocol] = Behaviors.receiveMessage {
      case AuthorizeCard(card, replyTo) =>
        if (card.startsWith("0")) { // validation logic
          replyTo ! PaymentDeclined
        } else {
          replyTo ! PaymentAccepted
        }
        Behaviors.same
      case _ => Behaviors.same
    }
  }

  object FulfillmentManager {
    def apply(): Behavior[PaymentProtocol] = active(43)

    def active(orderId: Int): Behavior[PaymentProtocol] = Behaviors.receive { (context, message) =>
      message match {
        case DispatchOrder(item, replyTo) =>
          context.log.info(s"Order $orderId for item $item has been dispatched.")
          replyTo ! OrderConfirmed
          active(orderId + 1)
        case _ => Behaviors.same
      }

    }
  }

}
