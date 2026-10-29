package pcd.ass01.simtrafficbase.version1;

import pcd.ass01.simseq.version1.Action;

/**
 * Car agent move forward action
 */
public record MoveForward(String agentId, double distance) implements Action {}
