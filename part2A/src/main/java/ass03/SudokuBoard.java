package ass03;

public class SudokuBoard {
    private static final int GRID_SIZE = 9;
    private final int[][] grid = new int[GRID_SIZE][GRID_SIZE];
    private final String[][] selectedBy = new String[GRID_SIZE][GRID_SIZE];
    private boolean[][] fixedCells = new boolean[GRID_SIZE][GRID_SIZE];
    private String[][] insertedBy = new String[GRID_SIZE][GRID_SIZE];

    public synchronized void setValue(int row, int col, int value) {
        grid[row][col] = value;
    }

    public synchronized int getValue(int row, int col) {
        return grid[row][col];
    }

    public synchronized void setSelection(int row, int col, String playerId) {
        selectedBy[row][col] = playerId;
    }

    public synchronized void clearSelection(int row, int col) {
        selectedBy[row][col] = null;
    }

    public synchronized String getSelectedBy(int row, int col) {
        return selectedBy[row][col];
    }

    public void loadInitialBoard() {
        int[][] board = SudokuGridGenerator.getRandomBoard();
        for (int r = 0; r < GRID_SIZE; r++) {
            for (int c = 0; c < GRID_SIZE; c++) {
                if (board[r][c] != 0) {
                    grid[r][c] = board[r][c];
                    fixedCells[r][c] = true;
                    insertedBy[r][c] = "SYSTEM";
                } else {
                    grid[r][c] = 0;
                    fixedCells[r][c] = false;
                    insertedBy[r][c] = null;
                }
            }
        }
    }

    public void clearBoard() {
        for (int r = 0; r < GRID_SIZE; r++) {
            for (int c = 0; c < GRID_SIZE; c++) {
                grid[r][c] = 0;
                fixedCells[r][c] = false;
                insertedBy[r][c] = null;
                selectedBy[r][c] = null;
            }
        }
    }

    public boolean isValidMove(int row, int col, int value) {
        if (fixedCells[row][col]) return false;
        if (value == 0) return true; // Needed for canceling a number
        if (value < 1 || value > 9) return false;

        for (int i = 0; i < GRID_SIZE; i++) {
            if (grid[row][i] == value && i != col) return false;
            if (grid[i][col] == value && i != row) return false;
        }

        int startR = (row / 3) * 3;
        int startC = (col / 3) * 3;
        for (int r = startR; r < startR + 3; r++) {
            for (int c = startC; c < startC + 3; c++) {
                if (grid[r][c] == value && (r != row || c != col)) return false;
            }
        }
        return true;
    }

    public synchronized boolean canSelect(int row, int col, String localPlayerId) {
        if (fixedCells[row][col]) return false;
        String currentSelector = selectedBy[row][col];
        return currentSelector == null || currentSelector.equals(localPlayerId);
    }

    public void setValue(int row, int col, int value, String playerId) {
        grid[row][col] = value;
        if (value == 0) {
            insertedBy[row][col] = null;
        } else {
            insertedBy[row][col] = playerId;
        }
    }

    public String getInsertedBy(int row, int col) {
        return insertedBy[row][col];
    }

    public synchronized String serializeState() {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < GRID_SIZE; r++) {
            for (int c = 0; c < GRID_SIZE; c++) {
                int val = grid[r][c];
                String sel = selectedBy[r][c] == null ? "NONE" : selectedBy[r][c];
                boolean isFixed = fixedCells[r][c];
                String inserter = insertedBy[r][c] == null ? "NONE" : insertedBy[r][c];

                if (val != 0 || !sel.equals("NONE") || isFixed) {
                    // Format: r,c,value,selector,isFixed,inserter;
                    sb.append(r).append(",").append(c).append(",").append(val).append(",")
                            .append(sel).append(",").append(isFixed).append(",").append(inserter).append(";");
                }
            }
        }
        return !sb.isEmpty() ? sb.toString() : "EMPTY";
    }

    public synchronized void deserializeState(String stateData) {
        if ("EMPTY".equals(stateData)) return;

        String[] cells = stateData.split(";");
        for (String cell : cells) {
            if (cell.isEmpty()) continue;
            String[] parts = cell.split(",");
            int r = Integer.parseInt(parts[0]);
            int c = Integer.parseInt(parts[1]);
            int val = Integer.parseInt(parts[2]);
            String sel = parts[3];
            boolean isFixed = Boolean.parseBoolean(parts[4]);
            String inserter = parts[5];

            grid[r][c] = val;
            fixedCells[r][c] = isFixed;
            if (!"NONE".equals(sel)) {
                selectedBy[r][c] = sel;
            }
            if (!"NONE".equals(inserter)) {
                insertedBy[r][c] = inserter;
            }
        }
    }
}