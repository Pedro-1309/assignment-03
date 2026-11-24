package ass03.model;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

// Vector Clock for tracking causal ordering
public class VectorClock {

    public enum Comparison {
        LESS, GREATER, EQUAL, CONCURRENT
    }

    private final Map<String, Integer> clock = new HashMap<>();

    public VectorClock() {
    }

    public VectorClock(String localPlayerId) {
        this.clock.put(localPlayerId, 0);
    }

    private VectorClock(Map<String, Integer> initialEntries) {
        this.clock.putAll(initialEntries);
    }

    public synchronized String incrementAndSerialize(String playerId) {
        int current = clock.getOrDefault(playerId, 0);
        clock.put(playerId, current + 1);
        return serializeInternal();
    }

    public synchronized void update(VectorClock receivedClock) {
        if (receivedClock == null) return;
        Map<String, Integer> otherSnapshot = receivedClock.snapshot();
        for (Map.Entry<String, Integer> entry : otherSnapshot.entrySet()) {
            String pId = entry.getKey();
            int receivedVal = entry.getValue();
            int localVal = clock.getOrDefault(pId, 0);
            clock.put(pId, Math.max(localVal, receivedVal));
        }
    }

    public synchronized void update(String serializedClock) {
        update(parse(serializedClock));
    }

    public synchronized void reset(String localPlayerId) {
        clock.clear();
        clock.put(localPlayerId, 0);
    }

    public synchronized String serialize() {
        return serializeInternal();
    }

    private String serializeInternal() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> entry : clock.entrySet()) {
            sb.append(entry.getKey()).append(":").append(entry.getValue()).append(",");
        }
        return !sb.isEmpty() ? sb.substring(0, sb.length() - 1) : "";
    }

    public synchronized Map<String, Integer> snapshot() {
        return new HashMap<>(clock);
    }

    public static VectorClock parse(String clockData) {
        Map<String, Integer> parsed = new HashMap<>();
        if (clockData == null || clockData.isEmpty()) {
            return new VectorClock(parsed);
        }

        String[] entries = clockData.split(",");
        for (String entry : entries) {
            String[] parts = entry.split(":");
            if (parts.length == 2) {
                try {
                    parsed.put(parts[0], Integer.parseInt(parts[1]));
                } catch (NumberFormatException ignored) {
                    // Ignore malformed tokens (e.g., messages without a clock)
                }
            }
        }
        return new VectorClock(parsed);
    }

    public static Comparison compare(VectorClock vc1, VectorClock vc2) {
        Map<String, Integer> c1 = (vc1 != null) ? vc1.snapshot() : Map.of();
        Map<String, Integer> c2 = (vc2 != null) ? vc2.snapshot() : Map.of();

        boolean lessOrEqual = true;
        boolean greaterOrEqual = true;
        boolean equal = true;

        Set<String> keys = new HashSet<>(c1.keySet());
        keys.addAll(c2.keySet());

        for (String key : keys) {
            int v1 = c1.getOrDefault(key, 0);
            int v2 = c2.getOrDefault(key, 0);

            if (v1 < v2) {
                greaterOrEqual = false;
                equal = false;
            } else if (v1 > v2) {
                lessOrEqual = false;
                equal = false;
            }
        }

        if (equal) return Comparison.EQUAL;
        if (lessOrEqual) return Comparison.LESS;       // vc1 happened before vc2
        if (greaterOrEqual) return Comparison.GREATER; // vc2 happened before vc1
        return Comparison.CONCURRENT;                  // Neither happened before the other
    }
}