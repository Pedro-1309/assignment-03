package ass03

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import ass03.SimulationController.{ControllerCommand, StartSimulation, ActionDecided}
import ass03.CarProtocol.CarCommand

object MassiveSimulationController {

  def apply(initialEnv: EnvState, cars: Seq[ActorRef[CarCommand]]): Behavior[ControllerCommand] =
    Behaviors.receive { (context, message) =>
      message match {
        case StartSimulation(steps) =>
          val startTime = System.currentTimeMillis()
          cars.foreach(_ ! CarProtocol.DoStep(0, initialEnv, context.self))
          running(0, steps, initialEnv, 0, cars, Map.empty, startTime)

        case _ => Behaviors.same
      }
    }

  private def running(
                       tick: Int,
                       targetSteps: Int,
                       envState: EnvState,
                       responsesCount: Int,
                       cars: Seq[ActorRef[CarCommand]],
                       decisions: Map[String, Double],
                       startTime: Long
                     ): Behavior[ControllerCommand] = Behaviors.receive { (context, message) =>
    message match {
      case ActionDecided(id, newSpeed) =>
        val newDecisions = decisions + (id -> newSpeed)

        if (responsesCount + 1 == cars.size) {
          val nextEnvState = envState.update(tick, newDecisions)
          val nextTick = tick + 1

          if (nextTick >= targetSteps) {
            val duration = System.currentTimeMillis() - startTime
            val avg = duration.toDouble / targetSteps
            println(s"[ SIMULATION ] Completed in $duration ms - average time per step: $avg ms")
            Behaviors.stopped
          } else {
            // Next tick, no waiting
            cars.foreach(_ ! CarProtocol.DoStep(nextTick, nextEnvState, context.self))
            running(nextTick, targetSteps, nextEnvState, 0, cars, Map.empty, startTime)
          }
        } else {
          // Waits for all cars
          running(tick, targetSteps, envState, responsesCount + 1, cars, newDecisions, startTime)
        }
      case _ => Behaviors.unhandled
    }
  }
}