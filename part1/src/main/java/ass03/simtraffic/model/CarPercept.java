package pcd.ass01.simtraffic.model;

import java.util.Optional;

import pcd.ass01.sim.engine.conc.*;
import pcd.ass01.sim.model.Percept;

/**
 * 
 * Percept for Car Agents
 * 
 * - position on the road
 * - nearest car, if present (distance)
 * - nearest semaphore, if present (distance)
 * 
 */
public record CarPercept(double roadPos, Optional<AbstractCar> nearestCarInFront, Optional<TrafficLightInfo> nearestSem) implements Percept { }