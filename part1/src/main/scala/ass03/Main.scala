package ass03

import akka.actor.typed.ActorSystem

object Main {
  def main(args: Array[String]): Unit = {
    val system = ActorSystem(SimulationRoot(), "traffic-simulation-system")

    // Optional: add a hook to terminate the system gracefully if needed
  }
}