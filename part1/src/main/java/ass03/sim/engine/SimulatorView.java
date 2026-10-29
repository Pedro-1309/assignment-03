package pcd.ass01.sim.engine;

import java.util.List;

import pcd.ass01.sim.model.AbstractAgent;
import pcd.ass01.sim.model.AbstractEnvironment;

public abstract class SimulatorView {

	abstract public void updateView(int t, AbstractEnvironment env, List<AbstractAgent> agents);
}
