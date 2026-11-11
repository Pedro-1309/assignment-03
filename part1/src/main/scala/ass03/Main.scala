package ass03

import akka.actor.typed.ActorSystem

object Main {
  def main(args: Array[String]): Unit = {

//    val rootBehavior = SimulationRoots.SingleRoadTwoCars()
//    val rootBehavior = SimulationRoots.SingleRoadWithTrafficLightTwoCars()
//    val rootBehavior = SimulationRoots.SingleRoadSeveralCars(numCars = 30)
    val rootBehavior = SimulationRoots.WithCrossRoads()
//    val rootBehavior = SimulationRoots.MassiveTest(numCars = 5000, steps = 100)

    val system = ActorSystem(rootBehavior, "traffic-simulation-system")
  }
}