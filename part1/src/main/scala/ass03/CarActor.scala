package ass03

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors

object CarActor {

  sealed trait CarCommand
  case class DoStep(tick: Int, envState: EnvState, replyTo: ActorRef[SimulationController.ControllerCommand]) extends CarCommand

  private val CarNearDistance: Double = 15.0
  private val CarFarEnoughDistance: Double = 20.0

  // The initial behavior maps to the STOPPED state
  def apply(id: String, maxSpeed: Double, acceleration: Double, deceleration: Double, speed: Double = 0.0): Behavior[CarCommand] =
    stopped(id, maxSpeed, acceleration, deceleration)

  // FSM states as functional behaviors
  private def stopped(id: String, maxSpeed: Double, acc: Double, dec: Double): Behavior[CarCommand] = Behaviors.receive { (context, message) =>
    message match {
      case DoStep(_, env, replyTo) =>
        val me = env.cars(id)
        val near = isCarNear(me, env, CarNearDistance)

        if (!near) {
          // STOPPED -> ACCELERATING (no near car)
          val newSpeed = Math.min(me.speed + acc, maxSpeed)
          replyTo ! SimulationController.ActionDecided(id, newSpeed)
          accelerating(id, maxSpeed, acc, dec)
        } else {
          replyTo ! SimulationController.ActionDecided(id, 0.0)
          Behaviors.same
        }
    }
  }

  private def accelerating(id: String, maxSpeed: Double, acc: Double, dec: Double): Behavior[CarCommand] = Behaviors.receive { (context, message) =>
    message match {
      case DoStep(_, env, replyTo) =>
        val me = env.cars(id)
        val near = isCarNear(me, env, CarNearDistance)

        if (near) {
          // ACCELERATING -> DECELERATING (near car detected)
          val newSpeed = Math.max(me.speed - dec, 0.0)
          replyTo ! SimulationController.ActionDecided(id, newSpeed)
          decelerating(id, maxSpeed, acc, dec)
        } else if (me.speed >= maxSpeed) {
          // ACCELERATING -> MOVING CONSTANT SPEED (max speed reached)
          replyTo ! SimulationController.ActionDecided(id, maxSpeed)
          movingConstantSpeed(id, maxSpeed, acc, dec)
        } else {
          // Stay in ACCELERATING
          val newSpeed = Math.min(me.speed + acc, maxSpeed)
          replyTo ! SimulationController.ActionDecided(id, newSpeed)
          Behaviors.same
        }
    }
  }

  private def movingConstantSpeed(id: String, maxSpeed: Double, acc: Double, dec: Double): Behavior[CarCommand] = Behaviors.receive { (context, message) =>
    message match {
      case DoStep(_, env, replyTo) =>
        val me = env.cars(id)
        val near = isCarNear(me, env, CarNearDistance)

        if (near) {
          // MOVING CONSTANT SPEED -> DECELERATING (near car detected)
          val newSpeed = Math.max(me.speed - dec, 0.0)
          replyTo ! SimulationController.ActionDecided(id, newSpeed)
          decelerating(id, maxSpeed, acc, dec)
        } else {
          replyTo ! SimulationController.ActionDecided(id, maxSpeed)
          Behaviors.same
        }
    }
  }

  private def decelerating(id: String, maxSpeed: Double, acc: Double, dec: Double): Behavior[CarCommand] = Behaviors.receive { (context, message) =>
    message match {
      case DoStep(_, env, replyTo) =>
        val me = env.cars(id)
        val near = isCarNear(me, env, CarFarEnoughDistance)

        if (me.speed <= 0.0) {
          // DECELERATING -> STOPPED
          replyTo ! SimulationController.ActionDecided(id, 0.0)
          stopped(id, maxSpeed, acc, dec)
        } else if (!near) {
          // DECELERATING -> WAIT A BIT (no near car detected)
          replyTo ! SimulationController.ActionDecided(id, me.speed)
          // Pass the internal timer state (e.g., 5 ticks) via method parameters
          waitABit(id, 20, maxSpeed, acc, dec)
        } else {
          // Stay in DECELERATING
          val newSpeed = Math.max(me.speed - dec, 0.0)
          replyTo ! SimulationController.ActionDecided(id, newSpeed)
          Behaviors.same
        }
    }
  }

  private def waitABit(id: String, ticksLeft: Int, maxSpeed: Double, acc: Double, dec: Double): Behavior[CarCommand] = Behaviors.receive { (context, message) =>
    message match {
      case DoStep(_, env, replyTo) =>
        val me = env.cars(id)

        if (ticksLeft <= 0) {
          // WAIT A BIT -> ACCELERATING (after little time)
          val newSpeed = Math.min(me.speed + acc, maxSpeed)
          replyTo ! SimulationController.ActionDecided(id, newSpeed)
          accelerating(id, maxSpeed, acc, dec)
        } else {
          replyTo ! SimulationController.ActionDecided(id, me.speed)
          waitABit(id, ticksLeft - 1, maxSpeed, acc, dec)
        }
    }
  }

  // Evaluates if there is a car directly in front within the sensing distance
  private def isCarNear(me: CarInfo, env: EnvState, sensingDistance: Double): Boolean = {
    env.cars.values.exists { other =>
      other.id != me.id &&
        // Handles also wrap-around (if the road loops)
        ((other.position > me.position && other.position - me.position < sensingDistance) ||
          (other.position < me.position && (env.streetLength - me.position + other.position) < sensingDistance))
    }
  }
}