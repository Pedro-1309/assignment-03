package ass03;

import java.awt.Color;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {
    private static final AtomicInteger localSelectedRow = new AtomicInteger(-1);
    private static final AtomicInteger localSelectedCol = new AtomicInteger(-1);

    public static void main(String[] args) {
        String localPlayerId = "Player-" + (int)(Math.random() * 1000);
        int totalPlayers = 4; // Expected total players for the vector clock

        SudokuView view = new SudokuView(localPlayerId);
        RabbitMQManager networkManager = new RabbitMQManager(localPlayerId, totalPlayers);
        SudokuBoard board = new SudokuBoard();

        try {
            networkManager.connect("localhost");

            view.setListener(new SudokuView.SudokuActionListener() {
                @Override
                public void onCellSelected(int row, int col) {
                    if (!board.canSelect(row, col, localPlayerId)) {
                        System.out.println("Cell selected by another player.");
                        return;
                    }

                    int currentRow = localSelectedRow.get();
                    int currentCol = localSelectedCol.get();

                    // Remove previous selection if it exists and is different from the new selection
                    if (currentRow != -1 && currentCol != -1 && (currentRow != row || currentCol != col)) {
                        networkManager.publishCellDeselection(currentRow, currentCol);
                    }

                    localSelectedRow.set(row);
                    localSelectedCol.set(col);
                    networkManager.publishCellSelection(row, col);
                }

                @Override
                public void onNumberInserted(int row, int col, int value) {
                    if (localSelectedRow.get() == row && localSelectedCol.get() == col) {
                        networkManager.publishNumberInsertion(row, col, value);
                    } else {
                        System.out.println("You must select a cell to insert a value.");
                    }
                }
            });

            // From network to model and view
            networkManager.startListening(message -> {
                String[] parts = message.split("\\|");
                String action = parts[0];
                String senderId = parts[1];
                int row = Integer.parseInt(parts[2]);
                int col = Integer.parseInt(parts[3]);

                if ("SELECT".equals(action)) {
                    board.setSelection(row, col, senderId);
                    Color playerColor = senderId.equals(localPlayerId) ? Color.CYAN : Color.YELLOW;
                    view.updateCellSelection(row, col, senderId, playerColor);
                }
                else if ("DESELECT".equals(action)) {
                    if (senderId.equals(board.getSelectedBy(row, col))) {
                        board.clearSelection(row, col);
                        view.clearCellSelection(row, col);
                    }
                }
                else if ("INSERT".equals(action)) {
                    int value = Integer.parseInt(parts[4]);

                    board.setValue(row, col, value);
                    board.clearSelection(row, col);

                    view.updateCellValue(row, col, value);
                    view.clearCellSelection(row, col);

                    // Remove my selection if I was the one who inserted the number
                    if (senderId.equals(localPlayerId)) {
                        localSelectedRow.set(-1);
                        localSelectedCol.set(-1);
                    }
                }
            });

            view.display();

        } catch (Exception e) {
            System.err.println("Failed to connect to RabbitMQ. Make sure the broker is running.");
        }
    }
}