package ass03.controller;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

// Failure detector with periodic PING heartbeats
public class HeartbeatManager {

    private static final long PING_INTERVAL_SEC = 3;
    private static final long CHECK_INTERVAL_SEC = 5;
    private static final long TIMEOUT_MS = 10_000;

    private final Map<String, Long> lastSeen = new ConcurrentHashMap<>();
    private ScheduledExecutorService executor;

    public synchronized void start(
            String localPlayerId,
            List<String> activePlayers,
            Runnable pingSender,
            Consumer<String> onPlayerCrashed) {

        stop();
        executor = Executors.newScheduledThreadPool(2);

        // Broadcast local heartbeat
        executor.scheduleAtFixedRate(pingSender, 0, PING_INTERVAL_SEC, TimeUnit.SECONDS);

        // Detect timed-out players
        executor.scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();
            for (String player : activePlayers) {
                if (player.equals(localPlayerId)) continue;

                long lastTime = lastSeen.getOrDefault(player, now);
                if (now - lastTime > TIMEOUT_MS) {
                    lastSeen.remove(player);
                    onPlayerCrashed.accept(player);
                }
            }
        }, CHECK_INTERVAL_SEC, CHECK_INTERVAL_SEC, TimeUnit.SECONDS);
    }

    public void recordHeartbeat(String playerId) {
        lastSeen.put(playerId, System.currentTimeMillis());
    }

    public void removePlayer(String playerId) {
        lastSeen.remove(playerId);
    }

    public synchronized void stop() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
        }
        executor = null;
        lastSeen.clear();
    }
}