package ass03.server;

public class SudokuBoard {
    private final int[][] grid = new int[9][9];
    private final boolean[][] fixedCells = new boolean[9][9];
    private final String[][] selectedBy = new String[9][9];
    private final String[][] insertedBy = new String[9][9];

    public SudokuBoard() {
        int[][] randomBoard = SudokuGridGenerator.getRandomBoard();
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                grid[r][c] = randomBoard[r][c];
                fixedCells[r][c] = grid[r][c] != 0;
                selectedBy[r][c] = null;
                insertedBy[r][c] = "SYSTEM";
            }
        }
    }

    public synchronized boolean isValidMove(int row, int col, int value) {
        if (fixedCells[row][col]) return false;

        if (value == 0) return true;

        if (value < 1 || value > 9) return false;

        // Check row and column
        for (int i = 0; i < 9; i++) {
            if (grid[row][i] == value && i != col) return false;
            if (grid[i][col] == value && i != row) return false;
        }

        // Check 3x3 subgrid
        int startR = (row / 3) * 3;
        int startC = (col / 3) * 3;
        for (int r = startR; r < startR + 3; r++) {
            for (int c = startC; c < startC + 3; c++) {
                if (grid[r][c] == value && (r != row || c != col)) return false;
            }
        }
        return true;
    }

    public synchronized boolean insertValue(int row, int col, int value, String playerId) {
        if (!playerId.equals(selectedBy[row][col])) return false;

        if (!isValidMove(row, col, value)) return false;

        grid[row][col] = value;
        if (value == 0) {
            insertedBy[row][col] = null;
        } else {
            insertedBy[row][col] = playerId;
        }
        // Release the selection lock
        selectedBy[row][col] = null;
        return true;
    }

    public synchronized boolean isComplete() {
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                if (grid[r][c] == 0) return false;
            }
        }
        return true;
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
