package ass03.common;

import java.rmi.Remote;
import java.rmi.RemoteException;

// Remote interface exported by the client to receive push notifications from the server
public interface ClientCallback extends Remote {

    void onCellSelected(int row, int col, String playerId) throws RemoteException;

    void onCellDeselected(int row, int col) throws RemoteException;

    void onNumberInserted(int row, int col, int value, String playerId) throws RemoteException;
}
