package ass03

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import ass03.simtrafficbase.version0.TrafficLight.TrafficLightState

import javax.swing.SwingUtilities

// Java classes
import ass03.simtrafficexamples.version0.RoadSimView
import ass03.simtrafficbase.version0.{RoadsEnv, P2d, CarAgentBasic}

object ViewActor {

  sealed trait ViewCommand
  case class UpdateView(tick: Int, envState: EnvState) extends ViewCommand
  case object Show extends ViewCommand

  def apply(): Behavior[ViewCommand] = Behaviors.setup { context =>

    var view: Option[RoadSimView] = None

    SwingUtilities.invokeLater(() => {
      view = Some(new RoadSimView())
    })

    Behaviors.receiveMessage {
      case Show =>
        SwingUtilities.invokeLater(() => {
          view.foreach(_.display())
        })
        Behaviors.same

      case UpdateView(tick, envState) =>
        SwingUtilities.invokeLater(() => {
          view.foreach { v =>
            val javaEnv = translateToJavaEnv(envState)
            v.notifyStepDone(tick, null, javaEnv)
          }
        })
        Behaviors.same
    }
  }
  
  private def translateToJavaEnv(scalaEnv: EnvState): RoadsEnv = {
    val javaEnv = new RoadsEnv()

    val startP = new P2d(0, 300)
    val endP = new P2d(scalaEnv.streetLength, 300)
    val road = javaEnv.createRoad(startP, endP)

    scalaEnv.cars.values.foreach { carInfo =>
      // Dummy agent
      val dummyAgent = new CarAgentBasic(carInfo.id, javaEnv, road, carInfo.position, 0.0, 0.0, 0.0)

      javaEnv.registerNewCar(dummyAgent, road, carInfo.position)
    }

    scalaEnv.trafficLights.values.foreach { semInfo =>
      val javaState = semInfo.state match {
        case "GREEN" => TrafficLightState.GREEN
        case "YELLOW" => TrafficLightState.YELLOW
        case "RED" => TrafficLightState.RED
        case _ => TrafficLightState.RED
      }

      val pos = new P2d(semInfo.position, 300)
      // Dummy traffic light
      val tl = javaEnv.createTrafficLight(pos, javaState, 1, 1, 1)
      road.addTrafficLight(tl, semInfo.position) //non è detto che serva
    }

    //Initialize environment (and traffic lights' states)
    javaEnv.init()
    
    javaEnv
  }
}