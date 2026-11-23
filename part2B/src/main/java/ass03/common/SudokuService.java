package ass03.common;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Map;

public interface SudokuService extends Remote {

    // --- Lobby operations ---
    void registerLobbyCallback(String playerId, LobbyCallback callback) throws RemoteException;

    void unregisterLobbyCallback(String playerId) throws RemoteException;

    Map<String, Integer> getActiveGames() throws RemoteException;

    String createGame() throws RemoteException;

    // --- Game operations ---
    // Atomically joins the room and returns the consistent initial snapshot (board and players)
    GameSnapshot joinGame(String gameId, String playerId, ClientCallback callback) throws RemoteException;

    void leaveGame(String gameId, String playerId) throws RemoteException;

    boolean selectCell(String gameId, int row, int col, String playerId) throws RemoteException;

    void deselectCell(String gameId, int row, int col, String playerId) throws RemoteException;

    boolean insertNumber(String gameId, int row, int col, int value, String playerId) throws RemoteException;
}