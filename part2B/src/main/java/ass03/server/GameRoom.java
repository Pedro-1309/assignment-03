package ass03.server;

import ass03.common.ClientCallback;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Encapsulates the state and active players of a single match
public class GameRoom {

    private final String gameId;
    private final SudokuBoard board;
    private final Map<String, ClientCallback> players = new ConcurrentHashMap<>();
    // Single-threaded executor -> client callbacks in FIFO order
    private final ExecutorService notificationExecutor = Executors.newSingleThreadExecutor();

    public GameRoom(String gameId) {
        this.gameId = gameId;
        this.board = new SudokuBoard();
    }

    public String getGameId() {
        return gameId;
    }

    public SudokuBoard getBoard() {
        return board;
    }

    public Map<String, ClientCallback> getPlayers() {
        return players;
    }

    public int getPlayersCount() {
        return players.size();
    }

    public Map<String, ClientCallback> snapshotPlayers() {
        return new HashMap<>(players);
    }

    // Enqueues a notification task without blocking the calling RMI thread
    public void enqueueNotification(Runnable task) {
        if (!notificationExecutor.isShutdown()) {
            notificationExecutor.execute(task);
        }
    }

    // Gracefully shuts down after all queued notifications
    public void shutdown() {
        notificationExecutor.shutdown();
    }
}
