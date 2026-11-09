package ass03

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import ass03.CarProtocol.{CarCommand, DoStep}

object CarActorExtended {

  private val CarNearDistance: Double = 15.0
  private val CarFarEnoughDistance: Double = 20.0
  private val SemNearDistance: Double = 100.0

  def apply(id: String, maxSpeed: Double, acceleration: Double, deceleration: Double, speed: Double = 0.0): Behavior[CarCommand] =
    stopped(id, maxSpeed, acceleration, deceleration)

  private def stopped(id: String, maxSpeed: Double, acc: Double, dec: Double): Behavior[CarCommand] = Behaviors.receive { (context, message) =>
    message match {
      case DoStep(_, env, replyTo) =>
        val me = env.cars(id)
        if (!isCarNear(me, env, CarNearDistance)) {
          // STOPPED -> ACCELERATING
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
        val brakingDist = (me.speed * me.speed) / (2 * dec)

        if (isCarNear(me, env, CarNearDistance + brakingDist)) {
          // ACCELERATING -> DECELERATING FOR A CAR
          val newSpeed = Math.max(me.speed - dec, 0.0)
          replyTo ! SimulationController.ActionDecided(id, newSpeed)
          deceleratingForCar(id, maxSpeed, acc, dec)
        } else if (isSemNearNotGreen(me, env, SemNearDistance)) {
          // ACCELERATING -> DECELERATING FOR A NOT GREEN SEM
          val newSpeed = Math.max(me.speed - dec, 0.0)
          replyTo ! SimulationController.ActionDecided(id, newSpeed)
          deceleratingForSem(id, maxSpeed, acc, dec)
        } else if (me.speed >= maxSpeed) {
          // ACCELERATING -> MOVING CONSTANT SPEED
          replyTo ! SimulationController.ActionDecided(id, maxSpeed)
          movingConstantSpeed(id, maxSpeed, acc, dec)
        } else {
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
        val brakingDist = (me.speed * me.speed) / (2 * dec)

        if (isCarNear(me, env, CarNearDistance + brakingDist)) {
          // MOVING CONSTANT SPEED -> DECELERATING FOR A CAR
          val newSpeed = Math.max(me.speed - dec, 0.0)
          replyTo ! SimulationController.ActionDecided(id, newSpeed)
          deceleratingForCar(id, maxSpeed, acc, dec)
        } else if (isSemNearNotGreen(me, env, SemNearDistance)) {
          // MOVING CONSTANT SPEED -> DECELERATING FOR A NOT GREEN SEM
          val newSpeed = Math.max(me.speed - dec, 0.0)
          replyTo ! SimulationController.ActionDecided(id, newSpeed)
          deceleratingForSem(id, maxSpeed, acc, dec)
        } else {
          replyTo ! SimulationController.ActionDecided(id, maxSpeed)
          Behaviors.same
        }
    }
  }

  private def deceleratingForCar(id: String, maxSpeed: Double, acc: Double, dec: Double): Behavior[CarCommand] = Behaviors.receive { (context, message) =>
    message match {
      case DoStep(_, env, replyTo) =>
        val me = env.cars(id)
        val brakingDist = (me.speed * me.speed) / (2 * dec)

        if (me.speed <= 0.0) {
          // DECELERATING FOR A CAR -> STOPPED
          replyTo ! SimulationController.ActionDecided(id, 0.0)
          stopped(id, maxSpeed, acc, dec)
        } else if (!isCarNear(me, env, CarFarEnoughDistance + brakingDist)) {
          // DECELERATING FOR A CAR -> WAIT A BIT
          replyTo ! SimulationController.ActionDecided(id, me.speed)
          waitABit(id, maxSpeed, acc, dec, 2)
        } else {
          val newSpeed = Math.max(me.speed - dec, 0.0)
          replyTo ! SimulationController.ActionDecided(id, newSpeed)
          Behaviors.same
        }
    }
  }

  private def waitABit(id: String, maxSpeed: Double, acc: Double, dec: Double, ticksLeft: Int): Behavior[CarCommand] = Behaviors.receive { (context, message) =>
    message match {
      case DoStep(_, env, replyTo) =>
        val me = env.cars(id)
        if (ticksLeft <= 0) {
          // WAIT A BIT -> ACCELERATING
          val newSpeed = Math.min(me.speed + acc, maxSpeed)
          replyTo ! SimulationController.ActionDecided(id, newSpeed)
          accelerating(id, maxSpeed, acc, dec)
        } else {
          replyTo ! SimulationController.ActionDecided(id, me.speed)
          waitABit(id, maxSpeed, acc, dec, ticksLeft - 1)
        }
    }
  }

  private def deceleratingForSem(id: String, maxSpeed: Double, acc: Double, dec: Double): Behavior[CarCommand] = Behaviors.receive { (context, message) =>
    message match {
      case DoStep(_, env, replyTo) =>
        val me = env.cars(id)

        // if it's green OR if we are gone past it and it's not close
        if (isSemNearGreen(me, env, SemNearDistance) ||
          (!isSemNearGreen(me, env, SemNearDistance) && !isSemNearNotGreen(me, env, SemNearDistance))) {
          // DECELERATING FOR A NOT GREEN SEM -> ACCELERATING
          val newSpeed = Math.min(me.speed + acc, maxSpeed)
          replyTo ! SimulationController.ActionDecided(id, newSpeed)
          accelerating(id, maxSpeed, acc, dec)
        } else if (me.speed <= 0.0) {
          // DECELERATING FOR A NOT GREEN SEM -> WAITING GREEN SEM
          replyTo ! SimulationController.ActionDecided(id, 0.0)
          waitingGreenSem(id, maxSpeed, acc, dec)
        } else {
          val newSpeed = Math.max(me.speed - dec, 0.0)
          replyTo ! SimulationController.ActionDecided(id, newSpeed)
          Behaviors.same
        }
    }
  }

  private def waitingGreenSem(id: String, maxSpeed: Double, acc: Double, dec: Double): Behavior[CarCommand] = Behaviors.receive { (context, message) =>
    message match {
      case DoStep(_, env, replyTo) =>
        val me = env.cars(id)

        if (isSemNearGreen(me, env, SemNearDistance)) {
          // WAITING GREEN SEM -> ACCELERATING
          val newSpeed = Math.min(me.speed + acc, maxSpeed)
          replyTo ! SimulationController.ActionDecided(id, newSpeed)
          accelerating(id, maxSpeed, acc, dec)
        } else {
          replyTo ! SimulationController.ActionDecided(id, 0.0)
          Behaviors.same
        }
    }
  }

  private def isCarNear(me: CarInfo, env: EnvState, threshold: Double): Boolean = {
    val roadLen = env.roads(me.roadId).length
    env.cars.values.exists { other =>
      other.id != me.id && other.roadId == me.roadId &&
        // Handles also wrap-around (if the road loops)
        ((other.position > me.position && other.position - me.position < threshold) ||
          (other.position < me.position && (roadLen - me.position + other.position) < threshold))
    }
  }

  private def isSemNearNotGreen(me: CarInfo, env: EnvState, threshold: Double): Boolean = {
    val roadLen = env.roads(me.roadId).length
    env.trafficLights.values.exists { sem =>
      sem.roadId == me.roadId && sem.state != "GREEN" && {
        val dist = if (sem.position >= me.position) sem.position - me.position else (roadLen - me.position + sem.position)
        dist < threshold
      }
    }
  }

  private def isSemNearGreen(me: CarInfo, env: EnvState, threshold: Double): Boolean = {
    val roadLen = env.roads(me.roadId).length
    env.trafficLights.values.exists { sem =>
      sem.roadId == me.roadId && sem.state == "GREEN" && {
        val dist = if (sem.position >= me.position) sem.position - me.position else (roadLen - me.position + sem.position)
        dist < threshold
      }
    }
  }
}
