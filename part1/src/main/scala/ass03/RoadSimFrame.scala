package ass03

import javax.swing.{JFrame, JLabel, SwingConstants}

class RoadSimFrame(width: Int, height: Int) extends JFrame("Traffic Simulation") {
  setSize(width, height)
  setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE)

  private val statusLabel = new JLabel("Tick: 0", SwingConstants.CENTER)
  getContentPane.add(statusLabel)

  def updateSimulation(tick: Int, envState: EnvState): Unit = {
    statusLabel.setText(s"Tick: $tick | Active cars: ${envState.cars.size}")
    repaint()
  }
}