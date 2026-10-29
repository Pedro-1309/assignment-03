package ass03.sim.engine;

import java.util.List;

import ass03.sim.model.AbstractAgent;
import ass03.sim.model.AbstractEnvironment;

public abstract class SimulatorView {

	abstract public void updateView(int t, AbstractEnvironment env, List<AbstractAgent> agents);
}
