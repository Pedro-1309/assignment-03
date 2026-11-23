package ass03.client;

import ass03.common.ClientCallback;
import ass03.common.LobbyCallback;
import ass03.common.SudokuService;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class SudokuClient {

    private static final AtomicInteger localSelectedRow = new AtomicInteger(-1);
    private static final AtomicInteger localSelectedCol = new AtomicInteger(-1);

    public static void main(String[] args) {
        String localPlayerId = "Player-" + (int) (Math.random() * 1000);
        LobbyView lobbyView = new LobbyView();

        try {
            Registry registry = LocateRegistry.getRegistry("localhost", 1099);
            SudokuService server = (SudokuService) registry.lookup("SudokuService");

            LobbyCallback lobbyCallback = new LobbyCallbackImpl(lobbyView);
            server.registerLobbyCallback(localPlayerId, lobbyCallback);

            Map<String, Integer> activeGames = server.getActiveGames();
            for (Map.Entry<String, Integer> entry : activeGames.entrySet()) {
                lobbyView.addAvailableGame(entry.getKey(), entry.getValue());
            }

            lobbyView.setListener(new LobbyView.LobbyActionListener() {
                @Override
                public void onJoinGame(String gameId) {
                    lobbyView.hideView();
                    startGame(gameId, localPlayerId, server, lobbyView);
                }

                @Override
                public void onCreateNewGame() {
                    try {
                        String newGameId = server.createGame();
                        lobbyView.hideView();
                        startGame(newGameId, localPlayerId, server, lobbyView);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            });

            lobbyView.display();

        } catch (Exception e) {
            System.err.println("Failed to connect to the RMI Server.");
            e.printStackTrace();
        }
    }

    private static void startGame(String gameId, String localPlayerId, SudokuService server, LobbyView lobbyView) {
        localSelectedRow.set(-1);
        localSelectedCol.set(-1);
        SudokuView view = new SudokuView(localPlayerId);

        try {
            ClientCallback callback = new ClientCallbackImpl(view);

            view.setListener(new SudokuView.SudokuActionListener() {
                @Override
                public void onCellSelected(int row, int col) {
                    try {
                        int currentRow = localSelectedRow.get();
                        int currentCol = localSelectedCol.get();

                        if (currentRow != -1 && currentCol != -1 && (currentRow != row || currentCol != col)) {
                            server.deselectCell(gameId, currentRow, currentCol, localPlayerId);
                        }

                        boolean success = server.selectCell(gameId, row, col, localPlayerId);
                        if (success) {
                            localSelectedRow.set(row);
                            localSelectedCol.set(col);
                        } else {
                            System.out.println("Cannot select cell (" + row + "," + col + ").");
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }

                @Override
                public void onNumberInserted(int row, int col, int value) {
                    try {
                        if (localSelectedRow.get() == row && localSelectedCol.get() == col) {
                            boolean success = server.insertNumber(gameId, row, col, value, localPlayerId);
                            if (success) {
                                localSelectedRow.set(-1);
                                localSelectedCol.set(-1);
                            } else {
                                view.showInvalidMoveDialog("Invalid number (" + value + ") for this row, column, or 3x3 grid.");
                            }
                        } else {
                            System.out.println("You must select the cell before inserting a number.");
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }

                @Override
                public void onQuit() {
                    try {
                        int currentRow = localSelectedRow.get();
                        int currentCol = localSelectedCol.get();
                        if (currentRow != -1 && currentCol != -1) {
                            server.deselectCell(gameId, currentRow, currentCol, localPlayerId);
                        }
                        server.leaveGame(gameId, localPlayerId);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    } finally {
                        lobbyView.display();
                    }
                }
            });

            server.joinGame(gameId, localPlayerId, callback);

            String initialState = server.getBoardState(gameId);
            parseAndLoadState(initialState, view);

            view.appendLog("Joined " + gameId);
            view.display();

        } catch (Exception e) {
            e.printStackTrace();
            lobbyView.display();
        }
    }

    // Deserialize the board string and update the UI
    private static void parseAndLoadState(String state, SudokuView view) {
        if (state == null || state.isEmpty() || "EMPTY".equals(state)) return;

        String[] cells = state.split(";");
        for (String cell : cells) {
            if (cell.isEmpty()) continue;

            String[] parts = cell.split(",");
            // Expected format: r, c, val, sel, isFixed, inserter
            if (parts.length >= 6) {
                int r = Integer.parseInt(parts[0]);
                int c = Integer.parseInt(parts[1]);
                int val = Integer.parseInt(parts[2]);
                String sel = parts[3];
                // boolean isFixed = Boolean.parseBoolean(parts[4]); // Available if the View needs it
                String inserter = parts[5];

                if (val != 0) {
                    String author = "NONE".equals(inserter) ? "SYSTEM" : inserter;
                    view.updateCellValue(r, c, val, author);
                }

                if (!"NONE".equals(sel)) {
                    view.updateCellSelection(r, c, sel);
                }
            }
        }
    }
}