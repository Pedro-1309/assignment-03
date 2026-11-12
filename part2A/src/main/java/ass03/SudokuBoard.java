package ass03;

public class SudokuBoard {
    private final int[][] grid = new int[9][9];
    private final String[][] selectedBy = new String[9][9];

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
}