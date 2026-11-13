package ass03;

public class SudokuBoard {
    private static final int GRID_SIZE = 9;
    private final int[][] grid = new int[GRID_SIZE][GRID_SIZE];
    private final String[][] selectedBy = new String[GRID_SIZE][GRID_SIZE];

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

    public synchronized boolean canSelect(int row, int col, String localPlayerId) {
        String currentSelector = selectedBy[row][col];
        return currentSelector == null || currentSelector.equals(localPlayerId);
    }

    public synchronized String serializeState() {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < GRID_SIZE; r++) {
            for (int c = 0; c < GRID_SIZE; c++) {
                int val = grid[r][c];
                String sel = selectedBy[r][c] == null ? "NONE" : selectedBy[r][c];
                if (val != 0 || !sel.equals("NONE")) {
                    // Format: r,c,value,selector;
                    sb.append(r).append(",").append(c).append(",").append(val).append(",").append(sel).append(";");
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

            grid[r][c] = val;
            if (!"NONE".equals(sel)) {
                selectedBy[r][c] = sel;
            }
        }
    }
}