package pcd.ass01.sim.model;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractSimulationModel {

	protected AbstractEnvironment env;
	protected List<AbstractAgent> agents;

	protected AbstractSimulationModel() {
		agents = new ArrayList<>();
	}
	
	public abstract void init();
		
	public void setEnvironment(AbstractEnvironment env) {
		this.env = env;
	}
		
	public AbstractEnvironment getEnvironment() {
		return env;
	}

	public void addAgent(AbstractAgent agent) {
		agents.add(agent);
		env.registerNewAgent(agent);
	}
	
	public  List<AbstractAgent> getAgents(){
		return agents;
	}

	
}
