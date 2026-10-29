package pcd.ass01.sim.engine.conc.cli;

import pcd.ass01.sim.engine.conc.ConcurrentSimulatorThreadBased;
import pcd.ass01.simtraffic.examples.*;
import pcd.common.Flag;

public class RunTrafficSimulationMassiveTest {

	public static void main(String[] args) {		

		int numCars = 5000;
		int nSteps = 100;
		int nWorkers = Runtime.getRuntime().availableProcessors() + 1;

		var simulationModel = new TrafficSimulationSingleRoadMassiveNumberOfCars(numCars);
		simulationModel.init();

		Flag stopFlag = new Flag();
		var simulator = new ConcurrentSimulatorThreadBased(nWorkers, stopFlag);
		simulator.setSimulationModel(simulationModel);
		simulator.setTimings(0, 1);

	
		/*
		RoadMassiveSimView view = new RoadMassiveSimView();
		view.display();
		simulation.addSimulationListener(view);		
		*/
		
		log("Running the simulation: " + numCars + " cars, for " + nSteps + " steps ...");
		
		simulator.run(nSteps, false);

		long d = simulator.getSimulationDuration();
		log("Completed in " + d + " ms - average time per step: " + simulator.getAverageTimePerStep() + " ms");
	}
	
	private static void log(String msg) {
		System.out.println("[ SIMULATION ] " + msg);
	}
}
