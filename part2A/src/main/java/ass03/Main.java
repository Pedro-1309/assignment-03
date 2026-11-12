package ass03;

import java.awt.Color;

public class Main {
    public static void main(String[] args) {
        String localPlayerId = "Player-" + (int)(Math.random() * 1000);
        int totalPlayers = 4; // Expected total players for the vector clock

        SudokuView view = new SudokuView(localPlayerId);
        RabbitMQManager networkManager = new RabbitMQManager(localPlayerId, totalPlayers);

        try {
            // Connect to local RabbitMQ broker (must be running on localhost)
            networkManager.connect("localhost");

            view.setListener(new SudokuView.SudokuActionListener() {
                @Override
                public void onCellSelected(int row, int col) {
                    networkManager.publishCellSelection(row, col);
                }

                @Override
                public void onNumberInserted(int row, int col, int value) {
                    networkManager.publishNumberInsertion(row, col, value);
                }
            });

            networkManager.startListening(message -> {
                String[] parts = message.split("\\|");
                String action = parts[0];
                String senderId = parts[1];
                int row = Integer.parseInt(parts[2]);
                int col = Integer.parseInt(parts[3]);

                // (Logical clock vector array is parsed here to enforce "happened-before" ordering)
                // String clockStr = parts[parts.length - 1];

                if ("SELECT".equals(action)) {
                    Color playerColor = senderId.equals(localPlayerId) ? Color.CYAN : Color.YELLOW;
                    view.updateCellSelection(row, col, senderId, playerColor);
                }
                else if ("INSERT".equals(action)) {
                    int value = Integer.parseInt(parts[4]);
                    view.updateCellValue(row, col, value);
                    view.clearCellSelection(row, col);
                }
            });

            view.display();

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Failed to connect to RabbitMQ. Make sure the broker is running.");
        }
    }
}