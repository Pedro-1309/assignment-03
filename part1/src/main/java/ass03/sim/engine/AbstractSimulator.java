package pcd.ass01.sim.engine;

import java.util.ArrayList;
import java.util.List;

import pcd.ass01.sim.model.*;

public abstract class AbstractSimulator {

	protected int dt;
	protected int t0;

	protected int nSteps;

	protected boolean syncWithTime;
	protected AbstractSimulationModel simModel;
	protected List<SimulationListener> listeners;

	
	/* for time statistics*/
	protected long startWallTime;
	protected long endWallTime;

	protected AbstractSimulator() {
		listeners = new ArrayList<>();
	}
	
	public void setSimulationModel(AbstractSimulationModel simModel) {
		this.simModel = simModel;
	}
	
	public AbstractSimulationModel getSimulationModel() {
		return simModel;
	}
	
	
	public abstract void run(int nSteps, boolean syncWithTime);
	
	public void setTimings(int t0, int dt) {
		this.dt = dt;
		this.t0 = t0;
	}
			
	public int getInitialTime() {
		return t0;
	}
	
	public int getTimeStep() {
		return dt;
	}
	
	public int getNumSteps() {
		return nSteps;
	}
		
	public void startedAt(long t) {
		this.startWallTime = t;
	}
	
	public void completedAt(long t) {
		this.endWallTime = t;
	}

	public long getSimulationDuration() {
		return endWallTime - startWallTime;
	}
	
	public long getAverageTimePerStep() {
		return getSimulationDuration()/nSteps;
	}

	
	public void addSimulationListener(SimulationListener l) {
		this.listeners.add(l);
	}
	
	public List<SimulationListener> getListeners(){
		return this.listeners;
	}
	
	public void notifyReset(int t0, List<AbstractAgent> agents, AbstractEnvironment env) {
		for (var l: listeners) {
			l.notifyInit(t0, agents, env);
		}
	}

	public void notifyNewStep(int t, List<AbstractAgent> agents, AbstractEnvironment env) {
		for (var l: listeners) {
			l.notifyStepDone(t, agents, env);
		}
	}
	
	
}
