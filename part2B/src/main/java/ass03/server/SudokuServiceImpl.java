package ass03.server;

import ass03.common.ClientCallback;
import ass03.common.SudokuService;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SudokuServiceImpl extends UnicastRemoteObject implements SudokuService {

    private final Map<String, ClientCallback> clients = new ConcurrentHashMap<>();
    private final SudokuBoard board;

    public SudokuServiceImpl(SudokuBoard board) throws RemoteException {
        super();
        this.board = board;
    }

    @Override
    public void joinGame(String playerId, ClientCallback callback) throws RemoteException {
        clients.put(playerId, callback);
        System.out.println("Player joined: " + playerId);
    }

    @Override
    public void leaveGame(String playerId) throws RemoteException {
        clients.remove(playerId);
        System.out.println("Player left: " + playerId);

        board.clearSelectionsByPlayer(playerId);
        broadcastCellDeselection(playerId);
    }

    @Override
    public String getBoardState() throws RemoteException {
        return board.serializeState();
    }

    @Override
    public boolean selectCell(int row, int col, String playerId) throws RemoteException {
        boolean success = board.setSelection(row, col, playerId);
        if (success) {
            // Push notification to all connected clients
            for (Map.Entry<String, ClientCallback> entry : clients.entrySet()) {
                try {
                    entry.getValue().onCellSelected(row, col, playerId);
                } catch (RemoteException e) {
                    System.err.println("Failed to notify " + entry.getKey());
                }
            }
        }
        return success;
    }

    @Override
    public void deselectCell(int row, int col, String playerId) throws RemoteException {
        board.clearSelection(row, col, playerId);
        // Push notification to all connected clients
        for (Map.Entry<String, ClientCallback> entry : clients.entrySet()) {
            try {
                entry.getValue().onCellDeselected(row, col);
            } catch (RemoteException e) {
                System.err.println("Failed to notify " + entry.getKey());
            }
        }
    }

    @Override
    public boolean insertNumber(int row, int col, int value, String playerId) throws RemoteException {
        boolean success = board.insertValue(row, col, value, playerId);
        if (success) {
            // Push notification to all connected clients
            for (Map.Entry<String, ClientCallback> entry : clients.entrySet()) {
                try {
                    entry.getValue().onNumberInserted(row, col, value, playerId);
                } catch (RemoteException e) {
                    System.err.println("Failed to notify " + entry.getKey());
                }
            }
        }
        return success;
    }

    // Helper method to release locks when a player leaves
    private void broadcastCellDeselection(String playerId) {
        // In a complete implementation, this would iterate over the board,
        // find cells previously locked by 'playerId', and broadcast onCellDeselected.
    }
}
