package ass03

import akka.actor.typed.ActorSystem

object Main {
  def main(args: Array[String]): Unit = {

//    val rootBehavior = SimulationRoots.SingleRoadTwoCars()
    val rootBehavior = SimulationRoots.SingleRoadWithTrafficLightTwoCars()
//    val rootBehavior = SimulationRoots.SingleRoadSeveralCars(numCars = 30)

    val system = ActorSystem(rootBehavior, "traffic-simulation-system")

    // Optional: add a hook to terminate the system gracefully if needed
  }
}