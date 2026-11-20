package ass03.client;

import ass03.common.LobbyCallback;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

// Remote object exported by the client to update LobbyView
public class LobbyCallbackImpl extends UnicastRemoteObject implements LobbyCallback {

    private final LobbyView lobbyView;

    public LobbyCallbackImpl(LobbyView lobbyView) throws RemoteException {
        super();
        this.lobbyView = lobbyView;
    }

    @Override
    public void onGameUpdated(String gameId, int playersCount) throws RemoteException {
        lobbyView.addAvailableGame(gameId, playersCount);
    }

    @Override
    public void onGameRemoved(String gameId) throws RemoteException {
        lobbyView.removeAvailableGame(gameId);
    }
}