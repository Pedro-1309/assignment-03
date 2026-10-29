package ass03.simtrafficbase.version0;

import ass03.simseq.version0.Action;

/**
 * Car agent move forward action
 */
public record MoveForward(double distance) implements Action {}
