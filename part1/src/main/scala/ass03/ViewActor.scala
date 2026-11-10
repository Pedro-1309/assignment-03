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
  case class Show(controller: akka.actor.typed.ActorRef[SimulationController.ControllerCommand]) extends ViewCommand

  def apply(): Behavior[ViewCommand] = Behaviors.setup { context =>

    var view: RoadSimView = null
    var controlFrame: SimulationControlFrame = null

    Behaviors.receiveMessage {
      case Show(controller) =>
        SwingUtilities.invokeLater(() => {
          view = new RoadSimView()
          view.display()

          controlFrame = new SimulationControlFrame(controller)
          controlFrame.setVisible(true)
        })
        Behaviors.same

      case UpdateView(tick, envState) =>
        SwingUtilities.invokeLater(() => {
          if (view != null) {
            val javaEnv = translateToJavaEnv(envState)
            view.notifyStepDone(tick, null, javaEnv)
          }
        })
        Behaviors.same
    }
  }
  
  private def translateToJavaEnv(scalaEnv: EnvState): RoadsEnv = {
    val javaEnv = new RoadsEnv()

    val javaRoads = scalaEnv.roads.map { case (rId, rInfo) =>
      val r = javaEnv.createRoad(new P2d(rInfo.startX, rInfo.startY), new P2d(rInfo.endX, rInfo.endY))
      rId -> r
    }

    scalaEnv.trafficLights.values.foreach { semInfo =>
      val javaState = semInfo.state match {
        case "GREEN" => TrafficLightState.GREEN
        case "YELLOW" => TrafficLightState.YELLOW
        case _ => TrafficLightState.RED
      }
      val tl = javaEnv.createTrafficLight(new P2d(semInfo.x, semInfo.y), javaState, 75, 25, 100)
      javaRoads(semInfo.roadId).addTrafficLight(tl, semInfo.position)
    }

    scalaEnv.cars.values.foreach { carInfo =>
      val road = javaRoads(carInfo.roadId)
      val dummyAgent = new CarAgentBasic(carInfo.id, javaEnv, road, carInfo.position, 0.0, 0.0, 0.0)
      javaEnv.registerNewCar(dummyAgent, road, carInfo.position)
    }

    //Initialize environment (and traffic lights' states)
    javaEnv.init()
    
    javaEnv
  }
}