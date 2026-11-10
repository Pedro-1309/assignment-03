package ass03

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

object SimulationRoots {

  def SingleRoadTwoCars(): Behavior[Nothing] = Behaviors.setup[Nothing] { context =>
    val r1 = RoadInfo("r1", 0.0, 300.0, 1500.0, 300.0, 1500.0)
    val initialCars = Map(
      "car-1" -> CarInfo("car-1", "r1", position = 0.0, speed = 0.0),
      "car-2" -> CarInfo("car-2", "r1", position = 100.0, speed = 0.0)
    )
    val initialEnv = EnvState(roads = Map("r1" -> r1), cars = initialCars)

    val viewActor = context.spawn(ViewActor(), "view-actor")
    
    val cars = Seq(
      context.spawn(CarActor("car-1", 8.0, 0.1, 0.2), "car-1"),
      context.spawn(CarActor("car-2", 7.0, 0.1, 0.1), "car-2")
    )

    val controller = context.spawn(SimulationController(viewActor, initialEnv, cars), "controller")
    
    viewActor ! ViewActor.Show(controller)

    // The root actor has no further messages to process
    Behaviors.empty
  }

  def SingleRoadSeveralCars(numCars: Int): Behavior[Nothing] = Behaviors.setup[Nothing] { context =>
    val r1 = RoadInfo("r1", 0.0, 300.0, 1500.0, 300.0, 1500.0)
    val initialCars = (0 until numCars).map { i =>
      val id = s"car-$i"
      id -> CarInfo(id, "r1", position = i * 10.0, speed = 0.0)
    }.toMap
    val initialEnv = EnvState(roads = Map("r1" -> r1), cars = initialCars)

    val viewActor = context.spawn(ViewActor(), "view-actor")

    val cars = (0 until numCars).map { i =>
      context.spawn(CarActor(s"car-$i", maxSpeed = 7.0, acceleration = 1.0, deceleration = 0.3), s"car-$i")
    }

    val controller = context.spawn(SimulationController(viewActor, initialEnv, cars), "controller")
    
    viewActor ! ViewActor.Show(controller)
    //controller ! SimulationController.StartSimulation(cars)
    Behaviors.empty
  }

  def SingleRoadWithTrafficLightTwoCars(): Behavior[Nothing] = Behaviors.setup[Nothing] { context =>
    val r1 = RoadInfo("r1", 0.0, 300.0, 1500.0, 300.0, 1500.0)
    
    val initialCars = Map(
      "car-1" -> CarInfo("car-1", "r1", position = 0.0, speed = 0.0),
      "car-2" -> CarInfo("car-2", "r1", position = 100.0, speed = 0.0)
    )

    val sem = SemInfo("sem-1", "r1", 740.0, "GREEN", 740.0, 300.0)

    val initialEnv = EnvState(roads = Map("r1" -> r1), cars = initialCars, trafficLights = Map("sem-1" -> sem))

    val viewActor = context.spawn(ViewActor(), "view-actor")

    val cars = Seq(
      context.spawn(CarActorExtended("car-1", 6.0, 0.1, 0.3), "car-1"),
      context.spawn(CarActorExtended("car-2", 5.0, 0.1, 0.3), "car-2")
    )

    val controller = context.spawn(SimulationController(viewActor, initialEnv, cars), "controller")
    
    viewActor ! ViewActor.Show(controller)
    //controller ! SimulationController.StartSimulation(cars)
    Behaviors.empty
  }

  def WithCrossRoads(): Behavior[Nothing] = Behaviors.setup[Nothing] { context =>

    val r1 = RoadInfo("r1", 0.0, 300.0, 1500.0, 300.0, 1500.0)
    val r2 = RoadInfo("r2", 750.0, 0.0, 750.0, 600.0, 600.0)

    val initialCars = Map(
      "car-1" -> CarInfo("car-1", "r1", 0.0, 0.0),
      "car-2" -> CarInfo("car-2", "r1", 100.0, 0.0),
      "car-3" -> CarInfo("car-3", "r2", 0.0, 0.0),
      "car-4" -> CarInfo("car-4", "r2", 100.0, 0.0)
    )

    val sem1 = SemInfo("sem-1", "r1", 740.0, "GREEN", 740.0, 300.0)
    val sem2 = SemInfo("sem-2", "r2", 290.0, "RED", 750.0, 290.0, tickOffset = 100)

    val initialEnv = EnvState(
      roads = Map("r1" -> r1, "r2" -> r2),
      cars = initialCars,
      trafficLights = Map("sem-1" -> sem1, "sem-2" -> sem2)
    )

    val viewActor = context.spawn(ViewActor(), "view-actor")

    val cars = Seq(
      context.spawn(CarActorExtended("car-1", 6.0, 0.1, 0.3), "car-1"),
      context.spawn(CarActorExtended("car-2", 5.0, 0.1, 0.3), "car-2"),
      context.spawn(CarActorExtended("car-3", 5.0, 0.1, 0.2), "car-3"),
      context.spawn(CarActorExtended("car-4", 4.0, 0.1, 0.1), "car-4")
    )

    val controller = context.spawn(SimulationController(viewActor, initialEnv, cars), "controller")
    
    viewActor ! ViewActor.Show(controller)
    //controller ! SimulationController.StartSimulation(cars)
    Behaviors.empty
  }
}