package ass03

import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors

object SimulationRoot {
  // Define the root behavior for the ActorSystem
  def apply(): Behavior[Unit] = Behaviors.setup { context =>

    val numCars = 2
    val initialCars = (1 to numCars).map { i =>
      val id = s"car-$i"
      id -> CarInfo(id, position = i * 20.0, speed = 0.0)
    }.toMap

    val initialEnv = EnvState(cars = initialCars)

    val viewActor = context.spawn(ViewActor(800, 600), "view-actor")
    viewActor ! ViewActor.Show

    val controller = context.spawn(SimulationController(viewActor, initialEnv), "simulation-controller")

    val carActors = (1 to numCars).map { i =>
      context.spawn(CarActor(s"car-$i", speed = 0.0), s"car-actor-$i")
    }

    // Send the start message to the controller to begin the simulation loop
    context.log.info("Creating simulation...")
    controller ! SimulationController.StartSimulation(carActors)

    // The root actor has no further messages to process
    Behaviors.empty
  }
}