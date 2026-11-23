package ass03.common;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

// Immutable object returned atomically to avoid race conditions
public class GameSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String boardState;
    private final List<String> activePlayers;

    public GameSnapshot(String boardState, List<String> activePlayers) {
        this.boardState = boardState;
        this.activePlayers = new ArrayList<>(activePlayers);
    }

    public String getBoardState() {
        return boardState;
    }

    public List<String> getActivePlayers() {
        return activePlayers;
    }
}