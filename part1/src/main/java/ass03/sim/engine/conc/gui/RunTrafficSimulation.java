package pcd.ass01.sim.engine.conc.gui;

import pcd.ass01.sim.engine.conc.ConcurrentSimulatorThreadBased;
import pcd.ass01.simtraffic.examples.*;

/**
 * 
 * Main class to create and run a simulation - with GUI
 * 
 */
public class RunTrafficSimulation {

	private static final int DEFAULT_STEPS = 10000;
	
	public static void main(String[] args) {		

		int nWorkers = Runtime.getRuntime().availableProcessors() + 1;

		// var simulation = new TrafficSimulationSingleRoadTwoCars();
		// var simulation = new TrafficSimulationSingleRoadSeveralCars();
		// var simulation = new TrafficSimulationSingleRoadWithTrafficLightTwoCars();
		
		var simulationModel = new TrafficSimulationWithCrossRoads();
		simulationModel.init();
		
        SimulationGUI gui = new SimulationGUI(DEFAULT_STEPS);

		SimulationController controller = new SimulationController(simulationModel, nWorkers);
		controller.attach(gui);
        gui.display();
				
	}
}
