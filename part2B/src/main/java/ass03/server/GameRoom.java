package ass03.server;

import ass03.common.ClientCallback;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Encapsulates the state and active players of a single match
public class GameRoom {

    private final String gameId;
    private final SudokuBoard board;
    private final Map<String, ClientCallback> players = new ConcurrentHashMap<>();

    public GameRoom(String gameId) {
        this.gameId = gameId;
        this.board = new SudokuBoard();
    }

    public String getGameId() {
        return gameId;
    }

    public SudokuBoard getBoard() {
        return board;
    }

    public Map<String, ClientCallback> getPlayers() {
        return players;
    }

    public int getPlayersCount() {
        return players.size();
    }
}
