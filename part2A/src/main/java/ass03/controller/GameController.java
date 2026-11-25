package ass03.controller;

import ass03.model.SudokuBoard;
import ass03.model.VectorClock;
import ass03.network.RabbitMQManager;
import ass03.view.LobbyView;
import ass03.view.SudokuView;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class GameController {

    private static final long LOBBY_STALE_TIMEOUT_MS = 6_000;

    private final String localPlayerId;
    private final LobbyView lobbyView;
    private final RabbitMQManager networkManager;
    private final SudokuBoard board;
    private final HeartbeatManager heartbeatManager;

    private final List<String> activePlayersList = new CopyOnWriteArrayList<>();
    private final AtomicInteger localSelectedRow = new AtomicInteger(-1);
    private final AtomicInteger localSelectedCol = new AtomicInteger(-1);
    private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);

    private final Map<String, Long> lobbyGameLastSeen = new ConcurrentHashMap<>();
    private final Map<String, Integer> lobbyGameCounts = new ConcurrentHashMap<>();
    private final ScheduledExecutorService lobbyMonitorExecutor = Executors.newSingleThreadScheduledExecutor();

    private volatile String currentGameId = null;
    private volatile boolean isGameOver = false;

    public GameController(String host) {
        this.localPlayerId = "Player-" + (int) (Math.random() * 1000);
        this.activePlayersList.add(localPlayerId);

        this.lobbyView = new LobbyView();
        this.networkManager = new RabbitMQManager(localPlayerId);
        this.board = new SudokuBoard();
        this.heartbeatManager = new HeartbeatManager();

        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdownCleanly));
        init(host);
    }

    private void init(String host) {
        try {
            networkManager.connect(host);
            setupLobbyNetworking();
            setupLobbyViewListeners();

            // Load unfinished games
            for (String gameId : networkManager.getRetainedLobbyGames()) {
                if (lobbyGameCounts.putIfAbsent(gameId, 0) == null) {
                    lobbyView.addAvailableGame(gameId, 0);
                }
            }

            networkManager.requestActiveGames();
            lobbyView.display();
        } catch (Exception e) {
            System.err.println("Failed to connect to RabbitMQ at " + host + ". Make sure the broker is running.");
            e.printStackTrace();
        }
    }

    private void setupLobbyNetworking() throws IOException {
        networkManager.joinLobby(new RabbitMQManager.LobbyListener() {
            @Override
            public void onListGamesRequest(String requesterId) {
                String gameId = currentGameId;
                // "Leader Election" between players to decide who will respond with game info
                if (gameId != null && !isGameOver && amITheLeader()) {
                    networkManager.publishGameInfo(gameId, activePlayersList.size());
                }
            }

            @Override
            public void onGameInfoReceived(String gameId, int playersCount) {
                if (playersCount <= -1) {
                    lobbyGameLastSeen.remove(gameId);
                    lobbyGameCounts.remove(gameId);
                    lobbyView.removeAvailableGame(gameId);
                } else {
                    lobbyGameCounts.put(gameId, playersCount);
                    if (playersCount > 0) {
                        lobbyGameLastSeen.put(gameId, System.currentTimeMillis());
                    } else {
                        lobbyGameLastSeen.remove(gameId);
                    }
                    lobbyView.addAvailableGame(gameId, playersCount);
                }
            }
        });

        // Detect matches where all players crashed
        lobbyMonitorExecutor.scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();
            for (Map.Entry<String, Long> entry : lobbyGameLastSeen.entrySet()) {
                String gameId = entry.getKey();
                if (now - entry.getValue() > LOBBY_STALE_TIMEOUT_MS) {
                    lobbyGameLastSeen.remove(gameId);
                    Integer count = lobbyGameCounts.get(gameId);
                    if (count != null && count > 0) {
                        lobbyGameCounts.put(gameId, 0);
                        lobbyView.addAvailableGame(gameId, 0);
                    }
                }
            }
        }, 3, 3, TimeUnit.SECONDS);
    }

    private void setupLobbyViewListeners() {
        lobbyView.setListener(new LobbyView.LobbyActionListener() {
            @Override
            public void onJoinGame(String gameId) {
                board.clearBoard(); // Reset local board
                lobbyView.hideView();
                startGame(gameId);
            }

            @Override
            public void onCreateNewGame() {
                String newGameId = "Match-" + (int) (Math.random() * 1000);
                board.clearBoard(); // Reset local board
                board.loadInitialBoard();
                try {
                    // Save the initial state
                    networkManager.retainGameState(newGameId, board.serializeState());
                    networkManager.addRetainedLobbyGame(newGameId);
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
                networkManager.publishGameInfo(newGameId, 1);
                lobbyView.hideView();
                startGame(newGameId);
            }

            @Override
            public void onExit() {
                // Run network disconnect off the Swing EDT before exiting
                new Thread(() -> {
                    shutdownCleanly();
                    System.exit(0);
                }).start();
            }
        });
    }

    private void startGame(String gameId) {
        this.currentGameId = gameId;
        this.isGameOver = false;
        this.localSelectedRow.set(-1);
        this.localSelectedCol.set(-1);
        this.activePlayersList.clear();
        this.activePlayersList.add(localPlayerId);

        SudokuView view = new SudokuView(localPlayerId);

        try {
            String savedState = networkManager.getRetainedGameState(gameId);
            if (!"EMPTY".equals(savedState)) {
                board.deserializeState(savedState);

                if (board.isComplete()) {
                    lobbyView.showInfoDialog("This game is already completed.", "Game Over");
                    try {
                        networkManager.removeRetainedLobbyGame(gameId);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    networkManager.publishGameInfo(gameId, -1);
                    currentGameId = null;
                    lobbyView.display();
                    return;
                }
            }

            renderBoardToView(view);

            // To update the number of active players in lobby view
            if (amITheLeader()) {
                networkManager.publishGameInfo(gameId, activePlayersList.size());
            }

            setupGameViewListeners(view, gameId);

            view.updateActivePlayers(activePlayersList);
            view.appendLog("Joined match: " + gameId);

            networkManager.joinGame(gameId, message -> handleIncomingMessage(message, gameId, view));

            heartbeatManager.start(
                    localPlayerId,
                    activePlayersList,
                    () -> {
                        if (!isGameOver && currentGameId != null) {
                            networkManager.publishPing();
                            // Leader refreshes lobby count so detects crashes
                            if (amITheLeader()) {
                                networkManager.publishGameInfo(gameId, activePlayersList.size());
                            }
                        }
                    },
                    crashedPlayer -> handlePlayerCrashed(crashedPlayer, gameId, view)
            );

            networkManager.publishJoin();
            view.display();
        } catch (Exception e) {
            System.err.println("Failed to start the game " + gameId);
            e.printStackTrace();
            currentGameId = null;
            lobbyView.display();
        }
    }

    private void renderBoardToView(SudokuView view) {
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

    private void setupGameViewListeners(SudokuView view, String gameId) {
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
                        view.showInvalidMoveDialog();
                    }
                } else {
                    System.out.println("You must select the cell before inserting a number.");
                }
            }

            @Override
            public void onQuit() {
                leaveCurrentGame(gameId);
            }
        });
    }

    private void leaveCurrentGame(String gameId) {
        int currentRow = localSelectedRow.getAndSet(-1);
        int currentCol = localSelectedCol.getAndSet(-1);

        if (currentRow != -1 && currentCol != -1) {
            networkManager.publishCellDeselection(currentRow, currentCol);
        }

        if (!isGameOver) {
            networkManager.publishQuit();
            if (activePlayersList.size() == 1 && activePlayersList.contains(localPlayerId)) {
                networkManager.publishGameInfo(gameId, 0);
            }
        }

        // Clean local state before network disconnects
        currentGameId = null;
        activePlayersList.clear();
        activePlayersList.add(localPlayerId);
        heartbeatManager.stop();

        try {
            networkManager.leaveGame();
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            lobbyView.display();
        }
    }

    // --- INCOMING MESSAGES ---

    private void handleIncomingMessage(String message, String gameId, SudokuView view) {
        String[] parts = message.split("\\|");
        if (parts.length < 2) return;

        String action = parts[0];
        String senderId = parts[1];

        // PING carries no vector clock
        if ("PING".equals(action)) {
            if (!senderId.equals(localPlayerId)) {
                heartbeatManager.recordHeartbeat(senderId);
            }
            return;
        }

        String clockData = parts[parts.length - 1];
        if (!senderId.equals(localPlayerId)) {
            networkManager.updateClock(clockData);
        }

        switch (action) {
            case "JOIN" -> handleJoin(senderId, gameId, view);
            case "SYNC_PLAYERS" -> handleSyncPlayers(parts, view);
            case "SELECT" -> handleSelect(parts, senderId, clockData, gameId, view);
            case "DESELECT" -> handleDeselect(parts, senderId, gameId, view);
            case "INSERT" -> handleInsert(parts, senderId, clockData, gameId, view);
            case "QUIT" -> handleQuit(senderId, gameId, view);
            case "GAME_OVER" -> {
                isGameOver = true;
                view.showGameCompleteDialog();
            }
            default -> System.err.println("Unknown action received: " + action);
        }
    }

    private void handleJoin(String senderId, String gameId, SudokuView view) {
        if (senderId.equals(localPlayerId)) return;

        boolean amILeader = amITheLeader();
        if (!activePlayersList.contains(senderId)) {
            activePlayersList.add(senderId);
            heartbeatManager.recordHeartbeat(senderId);
            view.updateActivePlayers(activePlayersList);
            view.appendLog(senderId + " connected.");
        }

        if (amILeader) {
            String playersStr = String.join(",", activePlayersList);
            networkManager.publishSyncPlayers(senderId, playersStr);
            networkManager.publishGameInfo(gameId, activePlayersList.size());
        }
    }

    private void handleSyncPlayers(String[] parts, SudokuView view) {
        String targetId = parts[2];
        String activePlayersStr = parts[3];

        if (targetId.equals(localPlayerId)) {
            activePlayersList.clear();
            for (String p : activePlayersStr.split(",")) {
                if (!p.isEmpty()) {
                    activePlayersList.add(p);
                    if (!p.equals(localPlayerId)) {
                        heartbeatManager.recordHeartbeat(p);
                    }
                }
            }
            view.updateActivePlayers(activePlayersList);
        }
    }

    private void handleSelect(String[] parts, String senderId, String clockData, String gameId, SudokuView view) {
        int row = Integer.parseInt(parts[2]);
        int col = Integer.parseInt(parts[3]);
        VectorClock msgClock = VectorClock.parse(clockData);

        if (board.setSelection(row, col, senderId, msgClock)) {
            view.updateCellSelection(row, col, senderId);
            persistState(gameId);
        } else if (senderId.equals(localPlayerId)) {
            localSelectedRow.set(-1);
            localSelectedCol.set(-1);
            view.appendLog("Selection in (" + row + "," + col + ") rejected: Concurrent conflict.");
        }
    }

    private void handleDeselect(String[] parts, String senderId, String gameId, SudokuView view) {
        int row = Integer.parseInt(parts[2]);
        int col = Integer.parseInt(parts[3]);

        if (senderId.equals(board.getSelectedBy(row, col))) {
            board.clearSelection(row, col);
            view.clearCellSelection(row, col);
            persistState(gameId);
        }
    }

    private void handleInsert(String[] parts, String senderId, String clockData, String gameId, SudokuView view) {
        int row = Integer.parseInt(parts[2]);
        int col = Integer.parseInt(parts[3]);
        int value = Integer.parseInt(parts[4]);
        VectorClock msgClock = VectorClock.parse(clockData);

        if (board.setValue(row, col, value, senderId, msgClock)) {
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
                persistState(gameId);
            }

            if (board.isComplete()) {
                isGameOver = true;
                if (senderId.equals(localPlayerId)) {
                    networkManager.publishGameOver();
                    networkManager.deleteRetainedGameState(gameId);
                    try {
                        networkManager.removeRetainedLobbyGame(gameId);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    networkManager.publishGameInfo(gameId, -1);
                }
            }
        } else if (senderId.equals(localPlayerId)) {
            view.appendLog("Move in (" + row + "," + col + ") rejected: Concurrent conflict.");
        }
    }

    private void handleQuit(String senderId, String gameId, SudokuView view) {
        if (senderId.equals(localPlayerId)) return;

        if (activePlayersList.remove(senderId)) {
            heartbeatManager.removePlayer(senderId);
            // Remove any selections
            board.clearSelectionsByPlayer(senderId);
            view.clearSelectionsByPlayer(senderId);

            view.updateActivePlayers(activePlayersList);
            view.appendLog(senderId + " disconnected.");

            // If the current leader left, elect a new one
            if (!isGameOver && amITheLeader()) {
                networkManager.publishGameInfo(gameId, activePlayersList.size());
            }
        }
    }

    private void handlePlayerCrashed(String crashedPlayer, String gameId, SudokuView view) {
        if (isGameOver || currentGameId == null) return;

        if (activePlayersList.remove(crashedPlayer)) {
            board.clearSelectionsByPlayer(crashedPlayer);
            view.clearSelectionsByPlayer(crashedPlayer);
            view.updateActivePlayers(activePlayersList);
            view.appendLog(crashedPlayer + " timed out (crashed).");

            if (amITheLeader()) {
                persistState(gameId);
                networkManager.publishGameInfo(gameId, activePlayersList.size());
            }
        }
    }

    private void persistState(String gameId) {
        try {
            networkManager.retainGameState(gameId, board.serializeState());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean amITheLeader() {
        if (activePlayersList.isEmpty()) return true;
        String leader = localPlayerId;
        for (String p : activePlayersList) {
            if (p.compareTo(leader) < 0) { // p is "smaller" than leader
                leader = p;
            }
        }
        return leader.equals(localPlayerId);
    }

    private void shutdownCleanly() {
        if (isShuttingDown.compareAndSet(false, true)) {
            String gameId = currentGameId;
            if (gameId != null && !isGameOver) {
                int currentRow = localSelectedRow.getAndSet(-1);
                int currentCol = localSelectedCol.getAndSet(-1);
                if (currentRow != -1 && currentCol != -1) {
                    networkManager.publishCellDeselection(currentRow, currentCol);
                }
                networkManager.publishQuit();
                if (activePlayersList.size() == 1 && activePlayersList.contains(localPlayerId)) {
                    networkManager.publishGameInfo(gameId, 0);
                }
            }
            heartbeatManager.stop();
            lobbyMonitorExecutor.shutdownNow();
            networkManager.disconnect();
        }
    }
}