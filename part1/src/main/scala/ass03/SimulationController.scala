package ass03

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{Behaviors, TimerScheduler}
import scala.concurrent.duration._
import ass03.CarProtocol.{CarCommand, DoStep}

object SimulationController {

  sealed trait ControllerCommand

  //case class StartSimulation(cars: Seq[ActorRef[CarCommand]]) extends ControllerCommand
  case class StartSimulation(steps: Int) extends ControllerCommand
  case object StopSimulation extends ControllerCommand

  case class ActionDecided(carId: String, newSpeed: Double) extends ControllerCommand

  private case object TriggerNextTick extends ControllerCommand

  // Immutable state
  def apply(
             view: ActorRef[ViewActor.ViewCommand],
             initialEnv: EnvState,
             cars: Seq[ActorRef[CarProtocol.CarCommand]]
           ): Behavior[ControllerCommand] =
    Behaviors.setup { context =>
      idle(view, initialEnv, cars)
    }

  private def idle(
                    view: ActorRef[ViewActor.ViewCommand],
                    envState: EnvState,
                    cars: Seq[ActorRef[CarProtocol.CarCommand]]
                  ): Behavior[ControllerCommand] =
    Behaviors.receive { (context, message) =>
      message match {
        case StartSimulation(steps) =>
          Behaviors.withTimers { timers =>
            timers.startSingleTimer(TriggerNextTick, 40.millis)
            running(timers, view, 0, steps, envState, 0, cars, Map.empty)
          }
        case _ => Behaviors.same
      }
    }

  private def running(
                       timers: TimerScheduler[ControllerCommand],
                       view: ActorRef[ViewActor.ViewCommand],
                       tick: Int,
                       targetSteps: Int,
                       envState: EnvState,
                       responsesCount: Int,
                       cars: Seq[ActorRef[CarProtocol.CarCommand]],
                       decisions: Map[String, Double]
                     ): Behavior[ControllerCommand] =
    Behaviors.receive { (context, message) =>
      message match {
        case StopSimulation =>
          timers.cancelAll()
          idle(view, envState, cars) // Break

        case ActionDecided(id, newSpeed) =>
          val newDecisions = decisions + (id -> newSpeed)
          if (responsesCount + 1 == cars.size) {

            val nextEnvState = envState.update(tick, newDecisions)
            view ! ViewActor.UpdateView(tick, nextEnvState)

            val nextTick = tick + 1

            if (nextTick >= targetSteps) {
              idle(view, nextEnvState, cars)
            } else {
              timers.startSingleTimer(TriggerNextTick, 40.millis)
              running(timers, view, nextTick, targetSteps, nextEnvState, 0, cars, Map.empty)
            }
          } else {
            running(timers, view, tick, targetSteps, envState, responsesCount + 1, cars, newDecisions)
          }
          
        case TriggerNextTick =>
          cars.foreach(_ ! DoStep(tick, envState, context.self))
          Behaviors.same
      }
    }

  private def updateEnvironment(currentEnv: EnvState): EnvState = {
    // Calcolo delle nuove posizioni basato sulle velocità ricevute
    currentEnv
  }
}