package ass03.common;

import java.rmi.Remote;
import java.rmi.RemoteException;

// Remote interface exported by the client to receive real-time lobby updates
public interface LobbyCallback extends Remote {

    void onGameUpdated(String gameId, int playersCount) throws RemoteException;

    void onGameRemoved(String gameId) throws RemoteException;
}