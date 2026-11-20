package ass03.client;

import ass03.common.ClientCallback;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;

public class ClientCallbackImpl extends UnicastRemoteObject implements ClientCallback {

    private final SudokuView view;

    public ClientCallbackImpl(SudokuView view) throws RemoteException {
        super();
        this.view = view;
    }

    @Override
    public void onCellSelected(int row, int col, String playerId) throws RemoteException {
        view.updateCellSelection(row, col, playerId);
    }

    @Override
    public void onCellDeselected(int row, int col) throws RemoteException {
        view.clearCellSelection(row, col);
    }

    @Override
    public void onNumberInserted(int row, int col, int value, String playerId) throws RemoteException {
        view.updateCellValue(row, col, value, playerId);
        view.clearCellSelection(row, col);

        String action = (value <= 0) ? " cleared cell " : " inserted " + value + " in ";
        view.appendLog(playerId + action + "(" + row + "," + col + ")");
    }

    @Override
    public void onPlayersUpdated(List<String> activePlayers, String logMessage) throws RemoteException {
        view.updateActivePlayers(activePlayers);
        if (logMessage != null && !logMessage.isEmpty()) {
            view.appendLog(logMessage);
        }
    }
}