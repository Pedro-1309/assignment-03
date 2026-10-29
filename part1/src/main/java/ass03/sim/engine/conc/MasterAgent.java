package pcd.ass01.sim.engine.conc;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Semaphore;

import pcd.ass01.sim.engine.AbstractSimulator;
import pcd.ass01.sim.model.AbstractAgent;
import pcd.common.Flag;
import pcd.common.Trigger;

public class MasterAgent extends Thread {
	
	private boolean toBeInSyncWithWallTime;
	private int nStepsPerSec;
	private int numSteps;

	private long currentWallTime;
	
	private AbstractSimulator simulator;
	private Flag stopFlag;
	private Semaphore done;
	private int nWorkers;
	
	public MasterAgent(AbstractSimulator sim, int numSteps, Flag stopFlag, Semaphore done, boolean syncWithTime, int nWorkers) {
		toBeInSyncWithWallTime = false;
		this.simulator = sim;
		this.stopFlag = stopFlag;
		this.numSteps = numSteps;
		this.done = done;
		this.nWorkers = nWorkers;
		
		if (syncWithTime) {
			this.syncWithTime(25);
		}
	}

	public void run() {
		
		log("booted");
		
		var simEnv = simulator.getSimulationModel().getEnvironment();
		var simAgents = simulator.getSimulationModel().getAgents();
		
		simEnv.init();
		for (var a: simAgents) {
			a.init(simEnv);
		}

		int t = simulator.getInitialTime();
		int dt = simulator.getTimeStep();
		
		simulator.notifyReset(t, simAgents, simEnv);
		
		Trigger canDoStep = new Trigger(nWorkers);
		CyclicBarrier jobDone = new CyclicBarrier(nWorkers + 1);
		
		log("creating workers...");
		
		int nAssignedAgentsPerWorker = simAgents.size()/nWorkers;

		int index = 0;
		List<WorkerAgent> workers = new ArrayList<>();
		for (int i = 0; i < nWorkers - 1; i++) {
			List<AbstractAgent> assignedSimAgents = new ArrayList<>();
			for (int j = 0; j < nAssignedAgentsPerWorker; j++) {
				assignedSimAgents.add(simAgents.get(index));
				index++;
			}
			
			WorkerAgent worker = new WorkerAgent("worker-"+i, assignedSimAgents, dt, canDoStep, jobDone, stopFlag);
			worker.start();
			workers.add(worker);
		}
		
		List<AbstractAgent> assignedSimAgents = new ArrayList<>();
		while (index < simAgents.size()) {
			assignedSimAgents.add(simAgents.get(index));
			index++;
		}

		WorkerAgent worker = new WorkerAgent("worker-"+(nWorkers-1), assignedSimAgents, dt, canDoStep, jobDone, stopFlag);
		worker.start();
		workers.add(worker);

		log("starting the simulation loop.");

		int step = 0;
		currentWallTime = System.currentTimeMillis();

		try {
			while (!stopFlag.isSet() &&  step < numSteps) {
				
				simEnv.step(dt);
				simEnv.cleanActions();

				/* trigger workers to do their work in this step */	
				canDoStep.trig();
				
				/* wait for workers to complete */
				jobDone.await();

				/* executed actions */
				simEnv.processActions();
								
				simulator.notifyNewStep(t, simAgents, simEnv);
	
				if (toBeInSyncWithWallTime) {
					syncWithWallTime();
				}
				
				/* updating logic time */
				
				t += dt;
				step++;
			}	
		} catch (Exception ex) {
			ex.printStackTrace();
		}

		log("done");
		stopFlag.set();
		canDoStep.trig();

		done.release();
	}

	protected void syncWithTime(int nStepsPerSec) {
		this.toBeInSyncWithWallTime = true;
		this.nStepsPerSec = nStepsPerSec;
	}

	protected void syncWithWallTime() {
		try {
			long newWallTime = System.currentTimeMillis();
			long delay = 1000 / this.nStepsPerSec;
			long wallTimeDT = newWallTime - currentWallTime;
			currentWallTime = System.currentTimeMillis();
			if (wallTimeDT < delay) {
				Thread.sleep(delay - wallTimeDT);
			}
		} catch (Exception ex) {}
		
	}
	
	protected void log(String msg) {
		System.out.println("[MASTER] " + msg);
	}
	
	
}
