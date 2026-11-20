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
import java.util.concurrent.atomic.AtomicInteger;

public class SudokuServiceImpl extends UnicastRemoteObject implements SudokuService {

    private final Map<String, GameRoom> games = new ConcurrentHashMap<>();
    private final Map<String, LobbyCallback> lobbyClients = new ConcurrentHashMap<>();
    private final AtomicInteger gameCounter = new AtomicInteger(1);

    public SudokuServiceImpl() throws RemoteException {
        super();
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
        if (room == null) return;

        room.getPlayers().remove(playerId);
        room.getBoard().clearSelectionsByPlayer(playerId);
        System.out.println(playerId + " left " + gameId);

        broadcastActivePlayers(room, playerId + " left the match.");
        // Keep the room alive even if playersCount drops to 0
        broadcastLobbyUpdate(gameId, room.getPlayersCount());
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
                    System.err.println("Failed to notify " + entry.getKey() + " on selectCell");
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
                System.err.println("Failed to notify " + entry.getKey() + " on deselectCell");
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
                } catch (RemoteException e) {
                    System.err.println("Failed to notify " + entry.getKey() + " on insertNumber");
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
                System.err.println("Failed to update players list for " + entry.getKey());
            }
        }
    }
}