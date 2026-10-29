package pcd.ass01.sim.engine.conc.gui;

import pcd.ass01.sim.engine.*;
import pcd.ass01.sim.engine.conc.*;
import pcd.ass01.sim.model.AbstractSimulationModel;
import pcd.ass01.simtraffic.examples.*;
import pcd.common.Flag;

public class SimulationController {

	private Flag stopFlag;
	private SimulationGUI gui;
	private RoadSimView view;
	private RoadSimStatistics stat;
	private ConcurrentSimulatorThreadBased simulator;
	 
	public SimulationController(AbstractSimulationModel simModel, int nWorkers) {
		this.stopFlag = new Flag();
		simulator = new ConcurrentSimulatorThreadBased(nWorkers, stopFlag);
		simulator.setSimulationModel(simModel);
		simulator.setTimings(0, 1);
		view = new RoadSimView();
		stat = new RoadSimStatistics();
		simulator.addSimulationListener(stat);
		simulator.addSimulationListener(view);		
	}
	
	public void attach(SimulationGUI gui) {
		this.gui = gui;		
		gui.setController(this);
	}

	public void notifyStarted(int nSteps) {
		new Thread(() -> {
			simulator.getSimulationModel().init();			
			view.display();
		
			stopFlag.reset();
			simulator.run(nSteps, true);
			gui.reset();
			
		}).start();
	}
	
	public void notifyStopped() {
		stopFlag.set();
	}

}
