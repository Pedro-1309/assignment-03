package ass03

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import scala.concurrent.duration._

object TrafficLightActor {

  sealed trait TrafficCommand
  private case object SwitchToYellow extends TrafficCommand
  private case object SwitchToRed extends TrafficCommand
  private case object SwitchToGreen extends TrafficCommand

  // 1 tick = 40ms
  private val GreenDuration = (75 * 40).millis
  private val YellowDuration = (25 * 40).millis
  private val RedDuration = (100 * 40).millis

  def apply(id: String, initialPosition: Double, controller: ActorRef[SimulationController.ControllerCommand]): Behavior[TrafficCommand] =
    Behaviors.setup { context =>
      Behaviors.withTimers { timers =>

        // Inizialize env with initial state (green)
        controller ! SimulationController.TrafficLightUpdated(SemInfo(id, initialPosition, "GREEN"))
        timers.startSingleTimer(SwitchToYellow, GreenDuration)
        greenState(id, initialPosition, controller, timers)
      }
    }

  private def greenState(id: String, pos: Double, controller: ActorRef[SimulationController.ControllerCommand], timers: akka.actor.typed.scaladsl.TimerScheduler[TrafficCommand]): Behavior[TrafficCommand] =
    Behaviors.receiveMessage {
      case SwitchToYellow =>
        controller ! SimulationController.TrafficLightUpdated(SemInfo(id, pos, "YELLOW"))
        timers.startSingleTimer(SwitchToRed, YellowDuration)
        yellowState(id, pos, controller, timers)
      case _ => Behaviors.unhandled
    }

  private def yellowState(id: String, pos: Double, controller: ActorRef[SimulationController.ControllerCommand], timers: akka.actor.typed.scaladsl.TimerScheduler[TrafficCommand]): Behavior[TrafficCommand] =
    Behaviors.receiveMessage {
      case SwitchToRed =>
        controller ! SimulationController.TrafficLightUpdated(SemInfo(id, pos, "RED"))
        timers.startSingleTimer(SwitchToGreen, RedDuration)
        redState(id, pos, controller, timers)
      case _ => Behaviors.unhandled
    }

  private def redState(id: String, pos: Double, controller: ActorRef[SimulationController.ControllerCommand], timers: akka.actor.typed.scaladsl.TimerScheduler[TrafficCommand]): Behavior[TrafficCommand] =
    Behaviors.receiveMessage {
      case SwitchToGreen =>
        controller ! SimulationController.TrafficLightUpdated(SemInfo(id, pos, "GREEN"))
        timers.startSingleTimer(SwitchToYellow, GreenDuration)
        greenState(id, pos, controller, timers)
      case _ => Behaviors.unhandled
    }
}