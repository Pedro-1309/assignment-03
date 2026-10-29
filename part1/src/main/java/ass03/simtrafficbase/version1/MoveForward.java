package ass03.simtrafficbase.version1;

import ass03.simseq.version1.Action;

/**
 * Car agent move forward action
 */
public record MoveForward(String agentId, double distance) implements Action {}
