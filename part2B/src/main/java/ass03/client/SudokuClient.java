package ass03.client;

import ass03.common.ClientCallback;
import ass03.common.SudokuService;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.concurrent.atomic.AtomicInteger;

public class SudokuClient {

    private static final AtomicInteger localSelectedRow = new AtomicInteger(-1);
    private static final AtomicInteger localSelectedCol = new AtomicInteger(-1);

    public static void main(String[] args) {
        String localPlayerId = "Player-" + (int)(Math.random() * 1000);
        SudokuView view = new SudokuView(localPlayerId);

        try {
            // Connect to the RMI registry and lookup the central server
            Registry registry = LocateRegistry.getRegistry("localhost", 1099);
            SudokuService server = (SudokuService) registry.lookup("SudokuService");

            ClientCallback callback = new ClientCallbackImpl(view);

            view.setListener(new SudokuView.SudokuActionListener() {
                @Override
                public void onCellSelected(int row, int col) {
                    try {
                        int currentRow = localSelectedRow.get();
                        int currentCol = localSelectedCol.get();

                        if (currentRow != -1 && currentCol != -1 && (currentRow != row || currentCol != col)) {
                            server.deselectCell(currentRow, currentCol, localPlayerId);
                        }

                        boolean success = server.selectCell(row, col, localPlayerId);
                        if (success) {
                            localSelectedRow.set(row);
                            localSelectedCol.set(col);
                        } else {
                            view.appendLog("Cannot select cell (" + row + "," + col + ") - locked or fixed.");
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }

                @Override
                public void onNumberInserted(int row, int col, int value) {
                    try {
                        if (localSelectedRow.get() == row && localSelectedCol.get() == col) {
                            boolean success = server.insertNumber(row, col, value, localPlayerId);
                            if (success) {
                                localSelectedRow.set(-1);
                                localSelectedCol.set(-1);
                            }
                        } else {
                            view.appendLog("You must select the cell before inserting a number.");
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
                            server.deselectCell(currentRow, currentCol, localPlayerId);
                        }
                        server.leaveGame(localPlayerId);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            });

            // Join the game, passing the callback to the server
            server.joinGame(localPlayerId, callback);
            String initialState = server.getBoardState();
            parseAndLoadState(initialState, view);

            view.appendLog("Connected to RMI Server successfully.");
            view.display();

        } catch (Exception e) {
            System.err.println("Failed to connect to the RMI Server.");
            e.printStackTrace();
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
