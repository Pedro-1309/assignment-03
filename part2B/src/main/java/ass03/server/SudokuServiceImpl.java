package ass03.server;

import ass03.common.ClientCallback;
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

    private void handlePlayerDisconnect(GameRoom room, String playerId) {
        ClientCallback removed = room.getPlayers().remove(playerId);
        if (removed == null) return;

        System.out.println(playerId + " left " + room.getGameId());

        List<int[]> clearedCells = room.getBoard().clearSelectionsByPlayer(playerId);
        for (int[] cell : clearedCells) {
            broadcastCellDeselected(room, cell[0], cell[1]);
        }

        broadcastActivePlayers(room, playerId + " left.");
        broadcastLobbyUpdate(room.getGameId(), room.getPlayersCount());
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
    public void joinGame(String gameId, String playerId, ClientCallback callback) throws RemoteException {
        GameRoom room = games.get(gameId);
        if (room == null) return;

        room.getPlayers().put(playerId, callback);
        System.out.println(playerId + " joined " + gameId);

        broadcastActivePlayers(room, playerId + " joined the match.");
        broadcastLobbyUpdate(gameId, room.getPlayersCount());
    }

    @Override
    public void leaveGame(String gameId, String playerId) throws RemoteException {
        GameRoom room = games.get(gameId);
        if (room != null) {
            handlePlayerDisconnect(room, playerId);
        }
    }

    @Override
    public String getBoardState(String gameId) throws RemoteException {
        GameRoom room = games.get(gameId);
        return (room != null) ? room.getBoard().serializeState() : "EMPTY";
    }

    @Override
    public boolean selectCell(String gameId, int row, int col, String playerId) throws RemoteException {
        GameRoom room = games.get(gameId);
        if (room == null) return false;

        boolean success = room.getBoard().setSelection(row, col, playerId);
        if (success) {
            for (Map.Entry<String, ClientCallback> entry : room.getPlayers().entrySet()) {
                try {
                    entry.getValue().onCellSelected(row, col, playerId);
                } catch (RemoteException e) {
                    handlePlayerDisconnect(room, entry.getKey());
                }
            }
        }
        return success;
    }

    @Override
    public void deselectCell(String gameId, int row, int col, String playerId) throws RemoteException {
        GameRoom room = games.get(gameId);
        if (room == null) return;

        room.getBoard().clearSelection(row, col, playerId);
        for (Map.Entry<String, ClientCallback> entry : room.getPlayers().entrySet()) {
            try {
                entry.getValue().onCellDeselected(row, col);
            } catch (RemoteException e) {
                handlePlayerDisconnect(room, entry.getKey());
            }
        }
    }

    @Override
    public boolean insertNumber(String gameId, int row, int col, int value, String playerId) throws RemoteException {
        GameRoom room = games.get(gameId);
        if (room == null) return false;

        boolean success = room.getBoard().insertValue(row, col, value, playerId);
        if (success) {
            for (Map.Entry<String, ClientCallback> entry : room.getPlayers().entrySet()) {
                try {
                    entry.getValue().onNumberInserted(row, col, value, playerId);

                    if (room.getBoard().isComplete()) {
                        for (Map.Entry<String, ClientCallback> e : room.getPlayers().entrySet()) {
                            try {
                                e.getValue().onGameCompleted();
                            } catch (RemoteException ex) {
                                System.err.println("Failed to notify " + e.getKey() + " on game completion");
                            }
                        }
                        // Remove the completed room
                        games.remove(gameId);
                        for (Map.Entry<String, LobbyCallback> e : lobbyClients.entrySet()) {
                            try {
                                e.getValue().onGameRemoved(gameId);
                            } catch (RemoteException ex) {
                                lobbyClients.remove(e.getKey());
                            }
                        }
                    }
                } catch (RemoteException e) {
                    handlePlayerDisconnect(room, entry.getKey());
                }
            }
        }
        return success;
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

    // Wrap in a serializable ArrayList for RMI transport
    private void broadcastActivePlayers(GameRoom room, String logMessage) {
        List<String> activePlayers = new ArrayList<>(room.getPlayers().keySet());

        for (Map.Entry<String, ClientCallback> entry : room.getPlayers().entrySet()) {
            try {
                entry.getValue().onPlayersUpdated(activePlayers, logMessage);
            } catch (RemoteException e) {
                handlePlayerDisconnect(room, entry.getKey());
            }
        }
    }

    private void broadcastCellDeselected(GameRoom room, int row, int col) {
        for (Map.Entry<String, ClientCallback> entry : room.getPlayers().entrySet()) {
            try {
                entry.getValue().onCellDeselected(row, col);
            } catch (RemoteException e) {
                handlePlayerDisconnect(room, entry.getKey());
            }
        }
    }
}