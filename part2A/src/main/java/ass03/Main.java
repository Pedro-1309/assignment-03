package ass03;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {
    private static final AtomicInteger localSelectedRow = new AtomicInteger(-1);
    private static final AtomicInteger localSelectedCol = new AtomicInteger(-1);

    private static List<String> activePlayersList = new ArrayList<>();
    private static final String myCurrentGameId = "Match-" + (int)(Math.random() * 100);
    private static String targetGameId = null;

    public static void main(String[] args) {
        String localPlayerId = "Player-" + (int)(Math.random() * 1000);
        activePlayersList.add(localPlayerId);

        SudokuView view = new SudokuView(localPlayerId);
        RabbitMQManager networkManager = new RabbitMQManager(localPlayerId);
        SudokuBoard board = new SudokuBoard();

        try {
            networkManager.connect("localhost");

            networkManager.joinLobby(new RabbitMQManager.LobbyListener() {
                @Override
                public void onListGamesRequest(String requesterId) {
                    // "Leader Election" between players to decide who will respond with game info
                    if (targetGameId != null && amITheLeader(localPlayerId, activePlayersList)) {
                        networkManager.publishGameInfo(myCurrentGameId, activePlayersList.size());
                    }
                }

                @Override
                public void onGameInfoReceived(String gameId, int playersCount) {
                    if (targetGameId == null) {
                        targetGameId = gameId;
                        System.out.println("Active game found: " + gameId + " with " + playersCount + " players.");
                    }
                }
            });

            networkManager.requestActiveGames();
            Thread.sleep(2000);

            if (targetGameId == null) {
                targetGameId = myCurrentGameId;
                System.out.println("No game found. Creating a new one: " + targetGameId);
            }

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

            networkManager.joinGame(targetGameId, message -> {
                        String[] parts = message.split("\\|");
                        String action = parts[0];
                        String senderId = parts[1];
                        String clockData = parts[parts.length - 1];

                        // Synchronize the local vector clock with the received clock data
                        if (!senderId.equals(localPlayerId)) {
                            networkManager.updateClock(clockData);
                        }

                        if ("STATE_REQUEST".equals(action)) {
                            // Ignore our own state requests
                            if (!senderId.equals(localPlayerId)) {
                                if (!activePlayersList.contains(senderId)) {
                                    activePlayersList.add(senderId);
                                }

                                if (amITheLeader(localPlayerId, activePlayersList)) {
                                    System.out.println("Sending state to " + senderId);
                                    networkManager.publishStateReply(senderId, board.serializeState());
                                }
                            }
                        }
                        // Recovery
                        else if ("STATE_REPLY".equals(action)) {
                            String targetId = parts[2];
                            String serializedBoard = parts[3];

                            // If I am the target, deserialize the state and update the view
                            if (targetId.equals(localPlayerId)) {
                                System.out.println("State received, synchronization...");
                                board.deserializeState(serializedBoard);

                                // Update View
                                for (int r = 0; r < 9; r++) {
                                    for (int c = 0; c < 9; c++) {
                                        int val = board.getValue(r, c);
                                        String sel = board.getSelectedBy(r, c);

                                        if (val != 0) {
                                            view.updateCellValue(r, c, val);
                                        }
                                        if (sel != null) {
                                            Color playerColor = sel.equals(localPlayerId) ? Color.CYAN : Color.YELLOW;
                                            view.updateCellSelection(r, c, sel, playerColor);
                                        }
                                    }
                                }
                            }
                        }
                        else if ("SELECT".equals(action)) {
                            int row = Integer.parseInt(parts[2]);
                            int col = Integer.parseInt(parts[3]);

                            board.setSelection(row, col, senderId);
                            Color playerColor = senderId.equals(localPlayerId) ? Color.CYAN : Color.YELLOW;
                            view.updateCellSelection(row, col, senderId, playerColor);
                        } else if ("DESELECT".equals(action)) {
                            int row = Integer.parseInt(parts[2]);
                            int col = Integer.parseInt(parts[3]);

                            if (senderId.equals(board.getSelectedBy(row, col))) {
                                board.clearSelection(row, col);
                                view.clearCellSelection(row, col);
                            }
                        } else if ("INSERT".equals(action)) {
                            int row = Integer.parseInt(parts[2]);
                            int col = Integer.parseInt(parts[3]);

                            int value = Integer.parseInt(parts[4]);
                            board.setValue(row, col, value);
                            board.clearSelection(row, col);

                            view.updateCellValue(row, col, value);
                            view.clearCellSelection(row, col);

                            if (senderId.equals(localPlayerId)) {
                                localSelectedRow.set(-1);
                                localSelectedCol.set(-1);
                            }
                        }
                    });
            // After listening on the exchange, we request the state from the others
            networkManager.publishStateRequest();
            view.display();

        } catch (Exception e) {
            System.err.println("Failed to connect to RabbitMQ. Make sure the broker is running.");
        }
    }

    private static boolean amITheLeader(String myId, List<String> players) {
        if (players.isEmpty()) return true;
        String leader = myId;
        for (String p : players) {
            if (p.compareTo(leader) < 0) {
                // p is "smaller" than leader
                leader = p;
            }
        }
        return leader.equals(myId);
    }
}