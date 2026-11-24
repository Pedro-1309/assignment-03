package ass03.view;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class SudokuView extends JFrame {

    private static final int GRID_SIZE = 9;

    private final JButton[][] cells = new JButton[GRID_SIZE][GRID_SIZE];
    private final String localPlayerId;
    private final DefaultListModel<String> playersListModel;
    private final JTextArea logArea;
    private final Map<String, Color> playerColors = new HashMap<>();
    private final Random random = new Random();
    private int lastClickedRow = -1;
    private int lastClickedCol = -1;

    private SudokuActionListener listener;

    public interface SudokuActionListener {
        void onCellSelected(int row, int col);
        void onNumberInserted(int row, int col, int value);
        void onQuit();
    }

    public SudokuView(String localPlayerId) {
        this.localPlayerId = localPlayerId;
        setTitle("Cooperative Sudoku - Player: " + localPlayerId);
        setSize(850, 600);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setLayout(new BorderLayout());

        // Grid
        JPanel gridPanel = new JPanel(new GridLayout(GRID_SIZE, GRID_SIZE));
        initializeGrid(gridPanel);
        gridPanel.setPreferredSize(new Dimension(600, 600));
        add(gridPanel, BorderLayout.CENTER);

        // Side Panel
        JPanel sidePanel = new JPanel(new BorderLayout());
        sidePanel.setPreferredSize(new Dimension(250, 600));
        sidePanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        playersListModel = new DefaultListModel<>();
        JList<String> playersList = new JList<>(playersListModel);
        playersList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                String displayStr = (String) value;

                // Extract the player ID (removes the " (You)" part if present)
                String playerId = displayStr.split(" ")[0];

                // Get player's color and make it darker
                Color pColor = getPlayerColor(playerId);
                c.setForeground(pColor.darker().darker());

                if (!isSelected) {
                    c.setBackground(Color.WHITE);
                }
                return c;
            }
        });
        JScrollPane playersScroll = new JScrollPane(playersList);
        playersScroll.setBorder(BorderFactory.createTitledBorder("Active Players"));
        playersScroll.setPreferredSize(new Dimension(230, 150));

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setLineWrap(true);
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder("Match Events"));

        JButton leaveBtn = new JButton("Leave Match");
        leaveBtn.setBackground(new Color(255, 100, 100));
        leaveBtn.setForeground(Color.WHITE);
        leaveBtn.setFocusPainted(false);
        leaveBtn.addActionListener(e -> handleQuit());

        JPanel topSidePanel = new JPanel(new BorderLayout());
        topSidePanel.add(playersScroll, BorderLayout.CENTER);
        sidePanel.add(topSidePanel, BorderLayout.NORTH);
        sidePanel.add(logScroll, BorderLayout.CENTER);
        sidePanel.add(leaveBtn, BorderLayout.SOUTH);

        add(sidePanel, BorderLayout.EAST);
        setupKeyBindings();

        // Closing the window -> leaving the game and notify the controller
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent windowEvent) {
                handleQuit();
            }
        });
    }

    private void handleQuit() {
        if (listener != null) {
            listener.onQuit();
        }
        dispose();
    }

    public void setListener(SudokuActionListener listener) {
        this.listener = listener;
    }

    private void initializeGrid(JPanel panel) {
        for (int row = 0; row < GRID_SIZE; row++) {
            for (int col = 0; col < GRID_SIZE; col++) {
                JButton btn = new JButton("");
                btn.setFont(new Font("Arial", Font.BOLD, 24));
                btn.setBackground(Color.WHITE);
                btn.setFocusPainted(false);

                // Borders 3x3
                int top = (row % 3 == 0) ? 3 : 1;
                int left = (col % 3 == 0) ? 3 : 1;
                int bottom = (row == 8) ? 3 : 1;
                int right = (col == 8) ? 3 : 1;
                btn.setBorder(BorderFactory.createMatteBorder(top, left, bottom, right, Color.BLACK));

                final int r = row;
                final int c = col;

                btn.addActionListener(e -> {
                    lastClickedRow = r;
                    lastClickedCol = c;
                    if (listener != null) {
                        listener.onCellSelected(r, c);
                    }
                });

                cells[row][col] = btn;
                panel.add(btn);
            }
        }
    }

    public void updateCellValue(int row, int col, int value, String playerId) {
        SwingUtilities.invokeLater(() -> {
            if (value == 0) {
                cells[row][col].setText("");
                cells[row][col].setForeground(Color.BLACK);
            } else {
                cells[row][col].setText(String.valueOf(value));
                if ("SYSTEM".equals(playerId)) {
                    cells[row][col].setForeground(Color.BLACK);
                } else {
                    Color pColor = getPlayerColor(playerId);
                    cells[row][col].setForeground(pColor.darker().darker());
                }
            }
        });
    }

    public void updateCellSelection(int row, int col, String playerId) {
        SwingUtilities.invokeLater(() -> {
            Color pColor = getPlayerColor(playerId);
            cells[row][col].setBackground(pColor);
        });
    }

    public void clearCellSelection(int row, int col) {
        SwingUtilities.invokeLater(() -> cells[row][col].setBackground(Color.WHITE));
    }

    public void clearSelectionsByPlayer(String disconnectedPlayerId) {
        SwingUtilities.invokeLater(() -> {
            Color targetColor = getPlayerColor(disconnectedPlayerId);
            for (int r = 0; r < GRID_SIZE; r++) {
                for (int c = 0; c < GRID_SIZE; c++) {
                    if (targetColor.equals(cells[r][c].getBackground())) {
                        cells[r][c].setBackground(Color.WHITE);
                    }
                }
            }
        });
    }

    private void setupKeyBindings() {
        InputMap inputMap = getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = getRootPane().getActionMap();

        // Digits 1-9
        for (int num = 1; num <= 9; num++) {
            final int value = num;
            String actionKey = "insert_" + num;

            inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_0 + num, 0), actionKey);
            inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_NUMPAD0 + num, 0), actionKey);

            actionMap.put(actionKey, new AbstractAction() {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    triggerNumberInsertion(value);
                }
            });
        }

        // Clear cell (0, Backspace, Delete)
        String clearKey = "clear_cell";
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_0, 0), clearKey);
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_NUMPAD0, 0), clearKey);
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), clearKey);
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), clearKey);

        actionMap.put(clearKey, new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                triggerNumberInsertion(0);
            }
        });
    }

    private void triggerNumberInsertion(int value) {
        if (listener != null && lastClickedRow != -1 && lastClickedCol != -1) {
            listener.onNumberInserted(lastClickedRow, lastClickedCol, value);
        }
    }

    public void updateActivePlayers(java.util.List<String> players) {
        SwingUtilities.invokeLater(() -> {
            playersListModel.clear();
            for (String p : players) {
                if (p.equals(localPlayerId)) {
                    playersListModel.addElement(p + " (You)");
                } else {
                    playersListModel.addElement(p);
                }
            }
        });
    }

    public void showInvalidMoveDialog() {
        SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(
                        this,
                        "Invalid move. Same number already exists in the row, column or 3x3 grid.",
                        "Invalid Move",
                        JOptionPane.WARNING_MESSAGE
                )
        );
    }

    public void showGameCompleteDialog() {
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(this,
                    "Congratulations! The board is complete.",
                    "Game Over",
                    JOptionPane.INFORMATION_MESSAGE);
            if (listener != null) {
                listener.onQuit();
            }
            dispose();
        });
    }

    public void appendLog(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength()); // Scroll to the bottom
        });
    }

    public void display() {
        SwingUtilities.invokeLater(() -> setVisible(true));
    }

    private Color getPlayerColor(String playerId) {
        if (playerId.equals(localPlayerId)) {
            return Color.CYAN; // Local player
        }
        return playerColors.computeIfAbsent(playerId, id -> getDifferentColor());
    }

    private Color getDifferentColor() {
        Random rand = new Random();
        Color newColor;
        boolean tooSimilar;
        int attempts = 0;

        do {
            tooSimilar = false;
            float randomHue = rand.nextFloat();
            newColor = Color.getHSBColor(randomHue, 1.0f, 1.0f);

            if (getColorDistance(newColor, Color.CYAN) < 100) {
                tooSimilar = true;
            }

            if (!tooSimilar) {
                for (Color existingColor : playerColors.values()) {
                    if (getColorDistance(newColor, existingColor) < 100) {
                        tooSimilar = true;
                        break;
                    }
                }
            }
            attempts++;
        } while (tooSimilar && attempts < 100);

        return newColor;
    }

    private double getColorDistance(Color c1, Color c2) {
        int rDiff = c1.getRed() - c2.getRed();
        int gDiff = c1.getGreen() - c2.getGreen();
        int bDiff = c1.getBlue() - c2.getBlue();
        return Math.sqrt(rDiff * rDiff + gDiff * gDiff + bDiff * bDiff);
    }
}
