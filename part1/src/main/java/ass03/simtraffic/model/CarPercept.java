package ass03.simtraffic.model;

import java.util.Optional;

import ass03.sim.engine.conc.*;
import ass03.sim.model.Percept;

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