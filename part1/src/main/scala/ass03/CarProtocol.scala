package ass03

import akka.actor.typed.ActorRef

object CarProtocol {
  
  sealed trait CarCommand

  case class DoStep(
                     tick: Int,
                     envState: EnvState,
                     replyTo: ActorRef[SimulationController.ControllerCommand]
                   ) extends CarCommand
}