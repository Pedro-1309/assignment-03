package pcd.ass01.sim.engine;

import java.util.List;

import pcd.ass01.sim.model.AbstractAgent;
import pcd.ass01.sim.model.AbstractEnvironment;

public interface SimulationListener {

	/**
	 * Called at the beginning of the simulation
	 * 
	 * @param t
	 * @param agents
	 * @param env
	 */
	void notifyInit(int t, List<AbstractAgent> agents, AbstractEnvironment env);
	
	/**
	 * Called at each step, updater all updates
	 * @param t
	 * @param agents
	 * @param env
	 */
	void notifyStepDone(int t, List<AbstractAgent> agents, AbstractEnvironment env);
}
