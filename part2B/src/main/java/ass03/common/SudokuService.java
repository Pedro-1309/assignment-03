package ass03.common;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface SudokuService extends Remote {

    // Allows a client to join and passes its callback reference to the server
    void joinGame(String playerId, ClientCallback callback) throws RemoteException;

    // Returns the serialized initial state of the board for late joiners
    String getBoardState() throws RemoteException;

    boolean selectCell(int row, int col, String playerId) throws RemoteException;

    void deselectCell(int row, int col, String playerId) throws RemoteException;

    boolean insertNumber(int row, int col, int value, String playerId) throws RemoteException;

    void leaveGame(String playerId) throws RemoteException;
}