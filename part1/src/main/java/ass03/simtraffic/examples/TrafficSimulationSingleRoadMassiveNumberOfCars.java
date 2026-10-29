package pcd.ass01.simtraffic.examples;

import pcd.ass01.sim.model.AbstractSimulationModel;
import pcd.ass01.simtraffic.model.*;

public class TrafficSimulationSingleRoadMassiveNumberOfCars extends AbstractSimulationModel {

	private int numCars;
	
	public TrafficSimulationSingleRoadMassiveNumberOfCars(int numCars) {
		super();
		this.numCars = numCars;
	}
	
	public void init() {

		RoadsEnv env = new RoadsEnv();
		this.setEnvironment(env);
		
		Road road = env.createRoad(new P2d(0,300), new P2d(15000,300));

		for (int i = 0; i < numCars; i++) {
			
			String carId = "car-" + i;
			double initialPos = i*10;			
			double carAcceleration = 1; //  + gen.nextDouble()/2;
			double carDeceleration = 0.3; //  + gen.nextDouble()/2;
			double carMaxSpeed = 7; // 4 + gen.nextDouble();
						
			AbstractCar car = new CarBasic(carId, env, 
									road,
									initialPos, 
									carAcceleration, 
									carDeceleration,
									carMaxSpeed);
			this.addAgent(car);
		}
		
	}	
}
	