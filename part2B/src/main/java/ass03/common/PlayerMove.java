package ass03.common;

import java.io.Serializable;

// Object passed through the network (serialized)
public class PlayerMove implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String playerId;
    private final int row;
    private final int col;
    private final int value;

    public PlayerMove(String playerId, int row, int col, int value) {
        this.playerId = playerId;
        this.row = row;
        this.col = col;
        this.value = value;
    }

    public String getPlayerId() { return playerId; }
    public int getRow() { return row; }
    public int getCol() { return col; }
    public int getValue() { return value; }
}
