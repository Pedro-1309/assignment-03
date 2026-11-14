package ass03;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.Map;

public class SudokuView extends JFrame {

    private static final int GRID_SIZE = 9;
    private final JButton[][] cells = new JButton[GRID_SIZE][GRID_SIZE];
    private SudokuActionListener listener;
    private final String localPlayerId;

    private final DefaultListModel<String> playersListModel;
    private final JList<String> playersList;
    private final JTextArea logArea;
    private final JButton leaveBtn;

    private final Map<String, Color> playerColors = new HashMap<>();

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
        playersList = new JList<>(playersListModel);
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

        leaveBtn = new JButton("Leave Match");
        leaveBtn.setBackground(new Color(255, 100, 100));
        leaveBtn.setForeground(Color.WHITE);
        leaveBtn.setFocusPainted(false);

        leaveBtn.addActionListener(e -> {
            if (listener != null) {
                listener.onQuit();
            }
            dispose();
        });

        JPanel topSidePanel = new JPanel(new BorderLayout());
        topSidePanel.add(playersScroll, BorderLayout.CENTER);
        sidePanel.add(topSidePanel, BorderLayout.NORTH);
        sidePanel.add(logScroll, BorderLayout.CENTER);
        sidePanel.add(leaveBtn, BorderLayout.SOUTH);

        add(sidePanel, BorderLayout.EAST);

        // Closing the window -> leaving the game and notify the controller
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                if (listener != null) {
                    listener.onQuit();
                }
                dispose();
            }
        });

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

                // Cell selection
                btn.addActionListener(e -> {
                    if (listener != null) {
                        listener.onCellSelected(r, c);
                    }
                });

                // Number insertion
                btn.addKeyListener(new KeyAdapter() {
                    @Override
                    public void keyTyped(KeyEvent e) {
                        char keyChar = e.getKeyChar();
                        if (listener != null) {
                            if (Character.isDigit(keyChar)) {
                                int value = Character.getNumericValue(keyChar);
                                if (listener != null) {
                                    listener.onNumberInserted(r, c, value);
                                }
                            } else if (keyChar == '\b' || keyChar == 127) { // Backspace or Canc
                                if (listener != null) {
                                    listener.onNumberInserted(r, c, 0); // 0 means cancel
                                }
                            }
                        }
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

    public void display() {
        SwingUtilities.invokeLater(() -> setVisible(true));
    }

    private Color getPlayerColor(String playerId) {
        if (playerId.equals(localPlayerId)) {
            return Color.CYAN; // Local player
        }
        return playerColors.computeIfAbsent(playerId, id -> {
            float hue = (Math.abs(id.hashCode()) % 360) / 360.0f;
            return Color.getHSBColor(hue, 1.0f, 1.0f);
        });
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

    public void appendLog(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength()); // Scroll to the bottom
        });
    }
}
