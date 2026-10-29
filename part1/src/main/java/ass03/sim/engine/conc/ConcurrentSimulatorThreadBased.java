package pcd.ass01.sim.engine.conc;

import java.util.concurrent.Semaphore;

import pcd.ass01.sim.engine.*;
import pcd.common.Flag;

public class ConcurrentSimulatorThreadBased extends AbstractSimulator
{

	private int nWorkers;
	private Flag stopFlag;
	
	public ConcurrentSimulatorThreadBased(int nWorkers, Flag stopFlag) {
		super();
		this.nWorkers = nWorkers;
		this.stopFlag = stopFlag;
	}
	
	public void run(int nSteps, boolean syncWithTime) {
		startWallTime = System.currentTimeMillis();
		
		Semaphore done = new Semaphore(0);
		MasterAgent agent = new MasterAgent(this, nSteps, stopFlag, done, syncWithTime, nWorkers);
		agent.start();
		
		try {
			done.acquire();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		
		endWallTime = System.currentTimeMillis();		
	}
}
