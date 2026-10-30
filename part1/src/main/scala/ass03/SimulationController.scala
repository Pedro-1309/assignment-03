package ass03

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{Behaviors, TimerScheduler}
import scala.concurrent.duration._

object SimulationController {

  sealed trait ControllerCommand

  case class StartSimulation(cars: Seq[ActorRef[CarActor.CarCommand]]) extends ControllerCommand

  case class ActionDecided(carId: String, newSpeed: Double) extends ControllerCommand

  private case object TriggerNextTick extends ControllerCommand

  // Immutable state
  def apply(view: ActorRef[ViewActor.ViewCommand], initialEnv: EnvState): Behavior[ControllerCommand] = Behaviors.setup { context =>
    Behaviors.withTimers { timers =>
      running(timers, view, 0, initialEnv, 0, Seq.empty, Map.empty)
    }
  }

  private def running(
                       timers: TimerScheduler[ControllerCommand],
                       view: ActorRef[ViewActor.ViewCommand],
                       tick: Int,
                       envState: EnvState,
                       responsesCount: Int,
                       cars: Seq[ActorRef[CarActor.CarCommand]],
                       decisions: Map[String, Double]
                     ): Behavior[ControllerCommand] =
    Behaviors.receive { (context, message) =>
      message match {

        case StartSimulation(carRefs) =>
          context.log.info("Simulation started.")
          carRefs.foreach(_ ! CarActor.DoStep(tick, envState, context.self))
          running(timers, view, tick, envState, 0, carRefs, Map.empty)

        case ActionDecided(carId, speed) =>
          val newResponsesCount = responsesCount + 1
          val newDecisions = decisions + (carId -> speed)

          // Check if all cars have responded
          if (newResponsesCount == cars.size) {
            // Logs only after 10 ticks
            if (tick % 10 == 0) {
              context.log.info(s"Tick $tick completed. Next...")
            }

            if (tick >= 1000) {
              context.log.info("Simulation ended (max ticks reached).")
              Behaviors.stopped
            } else {
              val nextEnvState = envState.update(newDecisions)
              val nextTick = tick + 1

              view ! ViewActor.UpdateView(tick, nextEnvState)

              // Timer asincrono di 50ms
              timers.startSingleTimer(TriggerNextTick, 50.millis)

              // Update the environment state
              running(timers, view, nextTick, nextEnvState, 0, cars, Map.empty)
            }
          } else {
            // Waits all responses
            running(timers, view, tick, envState, newResponsesCount, cars, newDecisions)
          }

        case TriggerNextTick =>
          cars.foreach(_ ! CarActor.DoStep(tick, envState, context.self))
          Behaviors.same
      }
    }

  private def updateEnvironment(currentEnv: EnvState): EnvState = {
    // Calcolo delle nuove posizioni basato sulle velocità ricevute
    currentEnv
  }
}