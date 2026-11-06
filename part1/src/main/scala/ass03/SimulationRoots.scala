package ass03

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

object SimulationRoots {

  def SingleRoadTwoCars(): Behavior[Nothing] = Behaviors.setup[Nothing] { context =>
    val initialCars = Map(
      "car-1" -> CarInfo("car-1", position = 0.0, speed = 0.0),
      "car-2" -> CarInfo("car-2", position = 100.0, speed = 0.0)
    )
    val initialEnv = EnvState(cars = initialCars, streetLength = 1500.0)

    val viewActor = context.spawn(ViewActor(), "view-actor")
    viewActor ! ViewActor.Show

    val controller = context.spawn(SimulationController(viewActor, initialEnv), "controller")

    val cars = Seq(
      context.spawn(CarActor("car-1", 8.0, 0.1, 0.2), "car-1"),
      context.spawn(CarActor("car-2", 7.0, 0.1, 0.1), "car-2")
    )

    controller ! SimulationController.StartSimulation(cars)
    // The root actor has no further messages to process
    Behaviors.empty
  }

  def SingleRoadSeveralCars(numCars: Int): Behavior[Nothing] = Behaviors.setup[Nothing] { context =>
    val initialCars = (0 until numCars).map { i =>
      val id = s"car-$i"
      id -> CarInfo(id, position = i * 10.0, speed = 0.0)
    }.toMap
    val initialEnv = EnvState(cars = initialCars)

    val viewActor = context.spawn(ViewActor(), "view-actor")
    viewActor ! ViewActor.Show

    val controller = context.spawn(SimulationController(viewActor, initialEnv), "controller")

    val cars = (0 until numCars).map { i =>
      context.spawn(CarActor(s"car-$i", maxSpeed = 7.0, acceleration = 1.0, deceleration = 0.3), s"car-$i")
    }

    controller ! SimulationController.StartSimulation(cars)
    Behaviors.empty
  }

  def SingleRoadWithTrafficLightTwoCars(): Behavior[Nothing] = Behaviors.setup[Nothing] { context =>
    val initialCars = Map(
      "car-1" -> CarInfo("car-1", position = 0.0, speed = 0.0),
      "car-2" -> CarInfo("car-2", position = 100.0, speed = 0.0)
    )
    val initialEnv = EnvState(cars = initialCars)

    val viewActor = context.spawn(ViewActor(), "view-actor")
    viewActor ! ViewActor.Show

    val controller = context.spawn(SimulationController(viewActor, initialEnv), "controller")

    val semActor = context.spawn(TrafficLightActor("sem-1", 740.0, controller), "sem-1")

    val cars = Seq(
      context.spawn(CarActorExtended("car-1", 6.0, 0.1, 0.3), "car-1"),
      context.spawn(CarActorExtended("car-2", 5.0, 0.1, 0.3), "car-2")
    )

    controller ! SimulationController.StartSimulation(cars)
    Behaviors.empty
  }
}