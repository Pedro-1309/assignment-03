package ass03

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import javax.swing.SwingUtilities

object ViewActor {

  sealed trait ViewCommand
  case class UpdateView(tick: Int, envState: EnvState) extends ViewCommand
  case object Show extends ViewCommand

  def apply(width: Int, height: Int): Behavior[ViewCommand] = Behaviors.setup { context =>

    // 1. Inizializzazione della GUI (fittizia per l'esempio, da sostituire con RoadSimView)
    // Usiamo invokeLater per rispettare le regole di Swing sui thread
    var frame: Option[RoadSimFrame] = None
    SwingUtilities.invokeLater(() => {
      frame = Some(new RoadSimFrame(width, height))
    })

    // 2. Comportamento reattivo
    Behaviors.receiveMessage {
      case Show =>
        SwingUtilities.invokeLater(() => {
          frame.foreach(_.setVisible(true))
        })
        Behaviors.same

      case UpdateView(tick, envState) =>
        // Inoltra il repaint al thread grafico
        SwingUtilities.invokeLater(() => {
          frame.foreach(_.updateSimulation(tick, envState))
        })
        Behaviors.same
    }
  }
}