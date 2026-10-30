package ass03

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors

object CarActor {
  sealed trait CarCommand
  case class DoStep(tick: Int, envState: EnvState, replyTo: ActorRef[SimulationController.ControllerCommand]) extends CarCommand

  // Behavior constructor -> new Behavior with an immutable state
  def apply(id: String, speed: Double, currentState: String = "STOPPED"): Behavior[CarCommand] =
    Behaviors.receive { (context, message) =>
      message match {
        case DoStep(tick, env, replyTo) =>
          // Sense: analize envState -> decide
          val (newSpeed, newState) = computeNextState(speed, currentState, env)

          // Act
          replyTo ! SimulationController.ActionDecided(id, newSpeed)

          // Update state (recursive)
          CarActor(id, newSpeed, newState)
      }
    }

  private def computeNextState(speed: Double, state: String, env: EnvState): (Double, String) = {
    (0.0, "STOPPED") // Placeholder
  }
}