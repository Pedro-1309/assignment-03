package ass03.server;

public class SudokuBoard {
    private final int[][] grid = new int[9][9];
    private final boolean[][] fixedCells = new boolean[9][9];
    private final String[][] selectedBy = new String[9][9];
    private final String[][] insertedBy = new String[9][9];

    public SudokuBoard() {
        // Load the default puzzle here (omitted for brevity)
    }

    // Uses locks (monitor) to prevent concurrent modifications
    public synchronized boolean setSelection(int row, int col, String playerId) {
        if (fixedCells[row][col]) return false;

        if (selectedBy[row][col] == null || selectedBy[row][col].equals(playerId)) {
            selectedBy[row][col] = playerId;
            return true;
        }
        return false;
    }

    public synchronized void clearSelection(int row, int col, String playerId) {
        if (playerId.equals(selectedBy[row][col])) {
            selectedBy[row][col] = null;
        }
    }

    public synchronized void clearSelectionsByPlayer(String playerId) {
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                if (playerId.equals(selectedBy[r][c])) {
                    selectedBy[r][c] = null;
                }
            }
        }
    }

    public synchronized boolean insertValue(int row, int col, int value, String playerId) {
        if (!playerId.equals(selectedBy[row][col])) return false;

        grid[row][col] = value;
        if (value == 0) {
            insertedBy[row][col] = null;
        } else {
            insertedBy[row][col] = playerId;
        }
        return true;
    }

    public synchronized String serializeState() {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                int val = grid[r][c];
                String sel = selectedBy[r][c] == null ? "NONE" : selectedBy[r][c];
                boolean isFixed = fixedCells[r][c];
                String inserter = insertedBy[r][c] == null ? "NONE" : insertedBy[r][c];

                // Append only non-empty or locked cells
                if (val != 0 || !sel.equals("NONE") || isFixed) {
                    sb.append(r).append(",").append(c).append(",").append(val)
                            .append(",").append(sel).append(",").append(isFixed)
                            .append(",").append(inserter).append(";");
                }
            }
        }
        return !sb.isEmpty() ? sb.toString() : "EMPTY";
    }
}
