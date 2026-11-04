package ass03

import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors

object SimulationRoot {
  // Define the root behavior for the ActorSystem
  def apply(): Behavior[Unit] = Behaviors.setup { context =>
    
    val initialCars = Map(
      "car-1" -> CarInfo("car-1", position = 0.0, speed = 0.0),
      "car-2" -> CarInfo("car-2", position = 100.0, speed = 0.0)
    )

    val initialEnv = EnvState(cars = initialCars)

    val viewActor = context.spawn(ViewActor(), "view-actor")
    viewActor ! ViewActor.Show

    val controller = context.spawn(SimulationController(viewActor, initialEnv), "simulation-controller")

    val semActor = context.spawn(TrafficLightActor("sem-1", 740.0, controller), "traffic-light-1")

    val carActors = Seq(
      context.spawn(CarActorExtended("car-1", maxSpeed = 6.0, acceleration = 0.1, deceleration = 0.3), "car-actor-1"),
      context.spawn(CarActorExtended("car-2", maxSpeed = 5.0, acceleration = 0.1, deceleration = 0.3), "car-actor-2")
    )

    // Send the start message to the controller to begin the simulation loop
    context.log.info("Creating simulation...")
    controller ! SimulationController.StartSimulation(carActors)

    // The root actor has no further messages to process
    Behaviors.empty
  }
}