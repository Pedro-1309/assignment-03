package ass03.simtraffic.examples;

import ass03.sim.engine.conc.*;
import ass03.sim.model.AbstractSimulationModel;
import ass03.simtraffic.model.*;

/**
 * 
 * Traffic Simulation about 2 cars moving on a single road, no traffic lights
 * 
 */
public class TrafficSimulationSingleRoadTwoCars extends AbstractSimulationModel {

	public TrafficSimulationSingleRoadTwoCars() {
		super();
	}
	
	public void init() {
		RoadsEnv env = new RoadsEnv();
		this.setEnvironment(env);
		
		Road r = env.createRoad(new P2d(0,300), new P2d(1500,300));
		AbstractCar car1 = new CarBasic("car-1", env, r, 0, 0.1, 0.2, 8);
		this.addAgent(car1);		
		AbstractCar car2 = new CarBasic("car-2", env, r, 100, 0.1, 0.1, 7);
		this.addAgent(car2);		
	}	
	
}
