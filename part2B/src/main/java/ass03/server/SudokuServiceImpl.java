package ass03.server;

import ass03.common.ClientCallback;
import ass03.common.GameSnapshot;
import ass03.common.LobbyCallback;
import ass03.common.SudokuService;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class SudokuServiceImpl extends UnicastRemoteObject implements SudokuService {

    private final Map<String, GameRoom> games = new ConcurrentHashMap<>();
    private final Map<String, LobbyCallback> lobbyClients = new ConcurrentHashMap<>();
    private final AtomicInteger gameCounter = new AtomicInteger(1);
    private final ScheduledExecutorService heartbeatExecutor = Executors.newScheduledThreadPool(1);

    public SudokuServiceImpl() throws RemoteException {
        super();
        startFailureDetector();
    }

    // Pings all connected clients to detect crashes
    private void startFailureDetector() {
        heartbeatExecutor.scheduleAtFixedRate(() -> {
            for (GameRoom room : games.values()) {
                for (Map.Entry<String, ClientCallback> entry : room.getPlayers().entrySet()) {
                    String playerId = entry.getKey();
                    try {
                        entry.getValue().ping();
                    } catch (RemoteException e) {
                        System.err.println("Heartbeat failed for " + playerId + " in " + room.getGameId());
                        handlePlayerDisconnect(room, playerId);
                    }
                }
            }
        }, 5, 5, TimeUnit.SECONDS);
    }

    // --- Lobby ---

    @Override
    public void registerLobbyCallback(String playerId, LobbyCallback callback) throws RemoteException {
        lobbyClients.put(playerId, callback);
    }

    @Override
    public void unregisterLobbyCallback(String playerId) throws RemoteException {
        lobbyClients.remove(playerId);
    }

    @Override
    public Map<String, Integer> getActiveGames() throws RemoteException {
        Map<String, Integer> snapshot = new HashMap<>();
        for (Map.Entry<String, GameRoom> entry : games.entrySet()) {
            snapshot.put(entry.getKey(), entry.getValue().getPlayersCount());
        }
        return snapshot;
    }

    @Override
    public String createGame() throws RemoteException {
        String gameId = "Match-" + gameCounter.getAndIncrement();
        GameRoom room = new GameRoom(gameId);
        games.put(gameId, room);

        System.out.println("Created new game room: " + gameId);
        broadcastLobbyUpdate(gameId, 0);
        return gameId;
    }

    // --- Game ---

    @Override
    public GameSnapshot joinGame(String gameId, String playerId, ClientCallback callback) throws RemoteException {
        GameRoom room = games.get(gameId);
        if (room == null) return null;

        GameSnapshot snapshot;
        int playersCount;

        synchronized (room) {
            if (games.get(gameId) != room) return null;
            room.getPlayers().put(playerId, callback);

            String boardState = room.getBoard().serializeState();
            List<String> activePlayers = new ArrayList<>(room.getPlayers().keySet());
            snapshot = new GameSnapshot(boardState, activePlayers);
            playersCount = room.getPlayersCount();

            Map<String, ClientCallback> recipients = room.snapshotPlayers();
            // Exclude the joining player from the join broadcast since they already receive the snapshot
            recipients.remove(playerId);

            room.enqueueNotification(() ->
                    notifyPlayersUpdated(room, recipients, activePlayers, playerId + " joined the match.")
            );
        }

        System.out.println(playerId + " joined " + gameId);
        broadcastLobbyUpdate(gameId, playersCount);
        return snapshot;
    }

    @Override
    public void leaveGame(String gameId, String playerId) throws RemoteException {
        GameRoom room = games.get(gameId);
        if (room != null) {
            handlePlayerDisconnect(room, playerId);
        }
    }

    @Override
    public boolean selectCell(String gameId, int row, int col, String playerId) throws RemoteException {
        GameRoom room = games.get(gameId);
        if (room == null) return false;

        synchronized (room) {
            boolean success = room.getBoard().setSelection(row, col, playerId);
            if (!success) return false;

            Map<String, ClientCallback> recipients = room.snapshotPlayers();
            room.enqueueNotification(() -> {
                for (Map.Entry<String, ClientCallback> entry : recipients.entrySet()) {
                    try {
                        entry.getValue().onCellSelected(row, col, playerId);
                    } catch (RemoteException e) {
                        handlePlayerDisconnect(room, entry.getKey());
                    }
                }
            });
            return true;
        }
    }

    @Override
    public void deselectCell(String gameId, int row, int col, String playerId) throws RemoteException {
        GameRoom room = games.get(gameId);
        if (room == null) return;

        synchronized (room) {
            room.getBoard().clearSelection(row, col, playerId);

            Map<String, ClientCallback> recipients = room.snapshotPlayers();
            room.enqueueNotification(() -> {
                for (Map.Entry<String, ClientCallback> entry : recipients.entrySet()) {
                    try {
                        entry.getValue().onCellDeselected(row, col);
                    } catch (RemoteException e) {
                        handlePlayerDisconnect(room, entry.getKey());
                    }
                }
            });
        }
    }

    @Override
    public boolean insertNumber(String gameId, int row, int col, int value, String playerId) throws RemoteException {
        GameRoom room = games.get(gameId);
        if (room == null) return false;

        boolean completed;
        synchronized (room) {
            boolean success = room.getBoard().insertValue(row, col, value, playerId);
            if (!success) return false;

            completed = room.getBoard().isComplete();
            Map<String, ClientCallback> recipients = room.snapshotPlayers();

            // Move notification
            room.enqueueNotification(() -> {
                for (Map.Entry<String, ClientCallback> entry : recipients.entrySet()) {
                    try {
                        entry.getValue().onNumberInserted(row, col, value, playerId);
                    } catch (RemoteException e) {
                        handlePlayerDisconnect(room, entry.getKey());
                    }
                }
            });

            // Board completion
            if (completed) {
                room.enqueueNotification(() -> {
                    for (Map.Entry<String, ClientCallback> entry : recipients.entrySet()) {
                        try {
                            entry.getValue().onGameCompleted();
                        } catch (RemoteException e) {
                            System.err.println("Failed to notify " + entry.getKey() + " on game completion");
                        }
                    }
                });
                games.remove(gameId);
                room.shutdown(); // Terminate after delivering the queued notifications
            }
        }

        if (completed) {
            broadcastLobbyRemoval(gameId);
        }
        return true;
    }

    private void handlePlayerDisconnect(GameRoom room, String playerId) {
        int playersCount;
        synchronized (room) {
            ClientCallback removed = room.getPlayers().remove(playerId);
            if (removed == null) return;

            List<int[]> clearedCells = room.getBoard().clearSelectionsByPlayer(playerId);
            List<String> activePlayers = new ArrayList<>(room.getPlayers().keySet());
            Map<String, ClientCallback> recipients = room.snapshotPlayers();
            playersCount = room.getPlayersCount();

            room.enqueueNotification(() -> {
                for (int[] cell : clearedCells) {
                    for (Map.Entry<String, ClientCallback> entry : recipients.entrySet()) {
                        try {
                            entry.getValue().onCellDeselected(cell[0], cell[1]);
                        } catch (RemoteException e) {
                            handlePlayerDisconnect(room, entry.getKey());
                        }
                    }
                }
                notifyPlayersUpdated(room, recipients, activePlayers, playerId + " left.");
            });
        }

        System.out.println(playerId + " left " + room.getGameId());
        if (games.containsKey(room.getGameId())) {
            broadcastLobbyUpdate(room.getGameId(), playersCount);
        }
    }

    private void notifyPlayersUpdated(GameRoom room, Map<String, ClientCallback> recipients,
                                      List<String> activePlayers, String logMessage) {
        for (Map.Entry<String, ClientCallback> entry : recipients.entrySet()) {
            try {
                entry.getValue().onPlayersUpdated(activePlayers, logMessage);
            } catch (RemoteException e) {
                handlePlayerDisconnect(room, entry.getKey());
            }
        }
    }

    private void broadcastLobbyUpdate(String gameId, int playersCount) {
        for (Map.Entry<String, LobbyCallback> entry : lobbyClients.entrySet()) {
            try {
                entry.getValue().onGameUpdated(gameId, playersCount);
            } catch (RemoteException e) {
                // Remove unreachable lobby clients
                lobbyClients.remove(entry.getKey());
            }
        }
    }

    private void broadcastLobbyRemoval(String gameId) {
        for (Map.Entry<String, LobbyCallback> entry : lobbyClients.entrySet()) {
            try {
                entry.getValue().onGameRemoved(gameId);
            } catch (RemoteException e) {
                lobbyClients.remove(entry.getKey());
            }
        }
    }
}