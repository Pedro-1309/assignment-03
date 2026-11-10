package ass03

import javax.swing._
import java.awt.{BorderLayout, FlowLayout}
import java.awt.event.{ActionEvent, ActionListener}
import akka.actor.typed.ActorRef

class SimulationControlFrame(controller: ActorRef[SimulationController.ControllerCommand]) extends JFrame {

  setTitle("Traffic Simulation Control")
  setSize(400, 100)
  setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE)
  setLayout(new BorderLayout())

  private val controlPanel = new JPanel(new FlowLayout())
  
  private val stepsInput = new JTextField("1000", 8)
  private val startBtn = new JButton("Start")
  private val stopBtn = new JButton("Stop")

  controlPanel.add(new JLabel("Steps:"))
  controlPanel.add(stepsInput)
  controlPanel.add(startBtn)
  controlPanel.add(stopBtn)

  add(controlPanel, BorderLayout.CENTER)

  stopBtn.setEnabled(false)

  startBtn.addActionListener(new ActionListener {
    override def actionPerformed(e: ActionEvent): Unit = {
      try {
        val steps = stepsInput.getText.toInt
        controller ! SimulationController.StartSimulation(steps)
        startBtn.setEnabled(false)
        stopBtn.setEnabled(true)
        stepsInput.setEnabled(false)
      } catch {
        case _: NumberFormatException =>
          JOptionPane.showMessageDialog(null, "Inserisci un numero di step valido.")
      }
    }
  })

  stopBtn.addActionListener(new ActionListener {
    override def actionPerformed(e: ActionEvent): Unit = {
      controller ! SimulationController.StopSimulation
      startBtn.setEnabled(true)
      stopBtn.setEnabled(false)
      stepsInput.setEnabled(true)
    }
  })
}
