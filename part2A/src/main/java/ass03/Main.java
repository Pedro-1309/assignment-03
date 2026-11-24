package ass03;

import ass03.model.SudokuBoard;
import ass03.network.RabbitMQManager;
import ass03.view.LobbyView;
import ass03.view.SudokuView;

import javax.swing.*;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {
    private static final AtomicInteger localSelectedRow = new AtomicInteger(-1);
    private static final AtomicInteger localSelectedCol = new AtomicInteger(-1);
    private static final List<String> activePlayersList = new CopyOnWriteArrayList<>();
    private static String targetGameId = null;
    private static boolean isGameOver = false;
    private static final Map<String, Long> lastSeen = new ConcurrentHashMap<>();
    private static ScheduledExecutorService heartbeatExecutor;

    public static void main(String[] args) {
        String localPlayerId = "Player-" + (int) (Math.random() * 1000);
        activePlayersList.add(localPlayerId);

        LobbyView lobbyView = new LobbyView();
        RabbitMQManager networkManager = new RabbitMQManager(localPlayerId);
        SudokuBoard board = new SudokuBoard();

        try {
            networkManager.connect("localhost");

            networkManager.joinLobby(new RabbitMQManager.LobbyListener() {
                @Override
                public void onListGamesRequest(String requesterId) {
                    // "Leader Election" between players to decide who will respond with game info
                    if (targetGameId != null && amITheLeader(localPlayerId, activePlayersList)) {
                        networkManager.publishGameInfo(targetGameId, activePlayersList.size());
                    }
                }

                @Override
                public void onGameInfoReceived(String gameId, int playersCount) {
                    if (playersCount <= -1) {
                        lobbyView.removeAvailableGame(gameId);
                    } else {
                        lobbyView.addAvailableGame(gameId, playersCount);
                    }
                }
            });

            networkManager.requestActiveGames();

            lobbyView.setListener(new LobbyView.LobbyActionListener() {
                @Override
                public void onJoinGame(String gameId) {
                    targetGameId = gameId;
                    board.clearBoard(); // Reset local board
                    lobbyView.hideView();
                    startGame(targetGameId, localPlayerId, networkManager, board, lobbyView);
                }

                @Override
                public void onCreateNewGame() {
                    targetGameId = "Match-" + (int)(Math.random() * 1000);
                    board.clearBoard(); // Reset local board
                    board.loadInitialBoard();
                    try {
                        // Save the initial state
                        networkManager.retainGameState(targetGameId, board.serializeState());
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                    networkManager.publishGameInfo(targetGameId, 1);
                    lobbyView.hideView();
                    startGame(targetGameId, localPlayerId, networkManager, board, lobbyView);
                }
            });

            lobbyView.display();
        } catch (Exception e) {
            System.err.println("Failed to connect to RabbitMQ. Make sure the broker is running.");
        }
    }

    private static void startGame(String targetGameId, String localPlayerId, RabbitMQManager networkManager, SudokuBoard board, LobbyView lobbyView) {
        isGameOver = false;
        SudokuView view = new SudokuView(localPlayerId);

        try {
            String savedState = networkManager.getRetainedGameState(targetGameId);
            if (!"EMPTY".equals(savedState)) {
                board.deserializeState(savedState);

                if (board.isComplete()) {
                    JOptionPane.showMessageDialog(lobbyView, "This game is already completed.", "Game Over", JOptionPane.INFORMATION_MESSAGE);
                    networkManager.publishGameInfo(targetGameId, -1);

                    try {
                        networkManager.leaveGame();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }

                    lobbyView.display();
                    return;
                }

                for (int r = 0; r < 9; r++) {
                    for (int c = 0; c < 9; c++) {
                        int val = board.getValue(r, c);
                        String sel = board.getSelectedBy(r, c);
                        String inserter = board.getInsertedBy(r, c);

                        if (val != 0) {
                            view.updateCellValue(r, c, val, inserter != null ? inserter : "SYSTEM");
                        }
                        if (sel != null) {
                            view.updateCellSelection(r, c, sel);
                        }
                    }
                }
            }

            // To update the number of active players in lobby view
            if (amITheLeader(localPlayerId, activePlayersList)) {
                networkManager.publishGameInfo(targetGameId, activePlayersList.size());
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
                        if (board.isValidMove(row, col, value)) {
                            networkManager.publishNumberInsertion(row, col, value);
                        } else {
                            JOptionPane.showMessageDialog(view, "Invalid move. Same number already exists in the row, column or 3x3 grid.");
                        }
                    } else {
                        System.out.println("You must select the cell before inserting a number.");
                    }
                }

                @Override
                public void onQuit() {
                    int currentRow = localSelectedRow.get();
                    int currentCol = localSelectedCol.get();

                    if (currentRow != -1 && currentCol != -1) {
                        networkManager.publishCellDeselection(currentRow, currentCol);
                    }

                    if (!isGameOver) {
                        networkManager.publishQuit();
                        if (activePlayersList.size() == 1 && activePlayersList.contains(localPlayerId)) {
                            networkManager.publishGameInfo(targetGameId, 0);
                        }
                    }

                    activePlayersList.clear();
                    activePlayersList.add(localPlayerId);

                    if (heartbeatExecutor != null && !heartbeatExecutor.isShutdown()) {
                        heartbeatExecutor.shutdown();
                    }
                    lastSeen.clear();

                    try {
                        networkManager.leaveGame();
                        lobbyView.display();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            });

            view.updateActivePlayers(activePlayersList);
            view.appendLog("Joined match: " + targetGameId);

            networkManager.joinGame(targetGameId, message -> {
                String[] parts = message.split("\\|");
                String action = parts[0];
                String senderId = parts[1];
                String clockData = parts[parts.length - 1];

                // Synchronize the local vector clock with the received clock data
                if (!senderId.equals(localPlayerId)) {
                    networkManager.updateClock(clockData);
                }

                if ("JOIN".equals(action)) {
                    if (!senderId.equals(localPlayerId)) {
                        boolean amILeader = amITheLeader(localPlayerId, activePlayersList);

                        if (!activePlayersList.contains(senderId)) {
                            activePlayersList.add(senderId);
                            view.updateActivePlayers(activePlayersList);
                            view.appendLog(senderId + " connected.");
                        }

                        if (amILeader) {
                            String playersStr = String.join(",", activePlayersList);
                            networkManager.publishSyncPlayers(senderId, playersStr);
                            networkManager.publishGameInfo(targetGameId, activePlayersList.size());
                        }
                    }
                }
                else if ("SYNC_PLAYERS".equals(action)) {
                    String targetId = parts[2];
                    String activePlayersStr = parts[3];

                    if (targetId.equals(localPlayerId)) {
                        activePlayersList.clear();
                        for (String p : activePlayersStr.split(",")) {
                            if (!p.isEmpty()) {
                                activePlayersList.add(p);
                            }
                        }
                        view.updateActivePlayers(activePlayersList);
                    }
                }
                else if ("SELECT".equals(action)) {
                    int row = Integer.parseInt(parts[2]);
                    int col = Integer.parseInt(parts[3]);
                    Map<String, Integer> msgClock = RabbitMQManager.parseClock(clockData);

                    boolean accepted = board.setSelection(row, col, senderId, msgClock);
                    if (accepted) {
                        view.updateCellSelection(row, col, senderId);
                        try {
                            networkManager.retainGameState(targetGameId, board.serializeState());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    } else {
                        if (senderId.equals(localPlayerId)) {
                            localSelectedRow.set(-1);
                            localSelectedCol.set(-1);
                            view.appendLog("Selection in (" + row + "," + col + ") rejected: Concurrent conflict.");
                        }
                    }
                } else if ("DESELECT".equals(action)) {
                    int row = Integer.parseInt(parts[2]);
                    int col = Integer.parseInt(parts[3]);

                    if (senderId.equals(board.getSelectedBy(row, col))) {
                        board.clearSelection(row, col);
                        view.clearCellSelection(row, col);
                        try {
                            networkManager.retainGameState(targetGameId, board.serializeState());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                } else if ("INSERT".equals(action)) {
                    int row = Integer.parseInt(parts[2]);
                    int col = Integer.parseInt(parts[3]);
                    int value = Integer.parseInt(parts[4]);
                    Map<String, Integer> msgClock = RabbitMQManager.parseClock(clockData);

                    boolean accepted = board.setValue(row, col, value, senderId, msgClock);
                    if (accepted) {
                        board.clearSelection(row, col);

                        view.updateCellValue(row, col, value, senderId);
                        view.clearCellSelection(row, col);
                        if (value == 0) {
                            view.appendLog(senderId + " cleared cell (" + row + "," + col + ")");
                        } else {
                            view.appendLog(senderId + " inserted " + value + " in cell (" + row + "," + col + ")");
                        }

                        if (senderId.equals(localPlayerId)) {
                            localSelectedRow.set(-1);
                            localSelectedCol.set(-1);
                            try {
                                networkManager.retainGameState(targetGameId, board.serializeState());
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }

                        if (board.isComplete()) {
                            isGameOver = true;
                            if (senderId.equals(localPlayerId)) {
                                networkManager.publishGameOver();
                                networkManager.deleteRetainedGameState(targetGameId);
                                networkManager.publishGameInfo(targetGameId, -1);
                            }
                        }
                    } else {
                        if (senderId.equals(localPlayerId)) {
                            view.appendLog("Move in (" + row + "," + col + ") rejected: Concurrent conflict.");
                        }
                    }
                } else if ("PING".equals(action)) {
                    if (!senderId.equals(localPlayerId)) {
                        lastSeen.put(senderId, System.currentTimeMillis());
                    }
                } else if ("QUIT".equals(action)) {
                    if (!senderId.equals(localPlayerId)) {
                        if (activePlayersList.contains(senderId)) {
                            activePlayersList.remove(senderId);
                            lastSeen.remove(senderId);
                            view.updateActivePlayers(activePlayersList);
                            view.appendLog(senderId + " disconnected.");

                            // Remove any selections
                            board.clearSelectionsByPlayer(senderId);
                            view.clearSelectionsByPlayer(senderId);

                            // If the current leader left, elect a new one
                            if (amITheLeader(localPlayerId, activePlayersList)) {
                                networkManager.publishGameInfo(targetGameId, activePlayersList.size());
                            }
                        }
                    }
                } else if ("GAME_OVER".equals(action)) {
                    isGameOver = true;
                    view.showGameCompleteDialog();
                }
            });
            // Load the initial board
            for (int r = 0; r < 9; r++) {
                for (int c = 0; c < 9; c++) {
                    int val = board.getValue(r, c);
                    if (val != 0) {
                        view.updateCellValue(r, c, val, board.getInsertedBy(r, c));
                    }
                }
            }

            heartbeatExecutor = Executors.newScheduledThreadPool(2);

            // Publish PING every 3 seconds
            heartbeatExecutor.scheduleAtFixedRate(() -> {
                if (!isGameOver && targetGameId != null) {
                    networkManager.publishPing();
                }
            }, 0, 3, TimeUnit.SECONDS);

            // Check map every 5 seconds
            heartbeatExecutor.scheduleAtFixedRate(() -> {
                if (isGameOver || targetGameId == null) return;

                long now = System.currentTimeMillis();
                boolean listChanged = false;

                for (String player : activePlayersList) {
                    if (player.equals(localPlayerId)) continue;

                    long lastTime = lastSeen.getOrDefault(player, now);
                    if (now - lastTime > 10000) {
                        activePlayersList.remove(player);
                        lastSeen.remove(player);
                        listChanged = true;
                        view.appendLog(player + " timed out (crashed).");
                        board.clearSelectionsByPlayer(player);
                        view.clearSelectionsByPlayer(player);
                    }
                }

                if (listChanged) {
                    view.updateActivePlayers(activePlayersList);
                    if (amITheLeader(localPlayerId, activePlayersList)) {
                        networkManager.publishGameInfo(targetGameId, activePlayersList.size());
                    }
                }
            }, 5, 5, TimeUnit.SECONDS);

            networkManager.publishJoin();
            view.display();
        } catch (Exception e) {
            System.err.println("Failed to start the game.");
            e.printStackTrace();
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