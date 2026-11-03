package ass03

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
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
    
    // 1. Ricrea la strada (es. una linea orizzontale lunga quanto la streetLength)
    val startP = new P2d(0, 300)
    val endP = new P2d(scalaEnv.streetLength, 300)
    val road = javaEnv.createRoad(startP, endP)

    // 2. Mappa le auto
    scalaEnv.cars.values.foreach { carInfo =>
      // Creiamo un agente dummy solo per soddisfare la firma di registerNewCar
      // Poiché la view legge solo la posizione e l'id, la logica dell'agente qui è ininfluente
      val dummyAgent = new CarAgentBasic(carInfo.id, javaEnv, road, carInfo.position, 0.0, 0.0, 0.0)

      javaEnv.registerNewCar(dummyAgent, road, carInfo.position)
    }
    
    javaEnv
  }
}