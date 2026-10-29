package pcd.ass01.sim.engine.conc.cli;

import pcd.ass01.sim.engine.conc.*;
import pcd.ass01.simtraffic.examples.*;
import pcd.common.Flag;

/**
 * 
 * Main class to create and run a simulation - CLI
 * 
 */
public class RunTrafficSimulation {

	private static final int DEFAULT_STEPS = 10000;

	public static void main(String[] args) {		

		int nWorkers = Runtime.getRuntime().availableProcessors() + 1;
		
		// var simulationModel = new TrafficSimulationSingleRoadTwoCars();
		// var simulationModel = new TrafficSimulationSingleRoadSeveralCars();
		// var simulationModel = new TrafficSimulationSingleRoadWithTrafficLightTwoCars();

		var simulationModel = new TrafficSimulationWithCrossRoads();
		simulationModel.init();
		
		Flag stopFlag = new Flag();
		var simulator = new ConcurrentSimulatorThreadBased(nWorkers, stopFlag);
		simulator.setSimulationModel(simulationModel);
		simulator.setTimings(0, 1);
		
		RoadSimStatistics stat = new RoadSimStatistics();
		RoadSimView view = new RoadSimView();
		view.display();
		
		simulator.addSimulationListener(stat);
		simulator.addSimulationListener(view);		
		
		simulator.run(DEFAULT_STEPS, true);
	}
}
