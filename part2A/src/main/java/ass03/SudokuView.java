package ass03;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

public class SudokuView extends JFrame {

    private final JButton[][] cells = new JButton[9][9];
    private SudokuActionListener listener;

    // Interfaccia per delegare le azioni utente al Controller/NetworkManager
    public interface SudokuActionListener {
        void onCellSelected(int row, int col);
        void onNumberInserted(int row, int col, int value);
    }

    public SudokuView(String localPlayerId) {
        setTitle("Cooperative Sudoku - Player: " + localPlayerId);
        setSize(600, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new GridLayout(9, 9));

        initializeGrid();
    }

    public void setListener(SudokuActionListener listener) {
        this.listener = listener;
    }

    private void initializeGrid() {
        for (int row = 0; row < 9; row++) {
            for (int col = 0; col < 9; col++) {
                JButton btn = new JButton("");
                btn.setFont(new Font("Arial", Font.BOLD, 24));
                btn.setBackground(Color.WHITE);
                btn.setFocusPainted(false);

                final int r = row;
                final int c = col;

                // 1. Gestione della SELEZIONE della casella (Click)
                btn.addActionListener(e -> {
                    if (listener != null) {
                        listener.onCellSelected(r, c);
                    }
                });

                // 2. Gestione dell'INSERIMENTO del valore (Tastiera)
                btn.addKeyListener(new KeyAdapter() {
                    @Override
                    public void keyTyped(KeyEvent e) {
                        char keyChar = e.getKeyChar();
                        if (listener != null) {
                            if (keyChar >= '1' && keyChar <= '9') {
                                listener.onNumberInserted(r, c, keyChar - '0');
                            } else if (keyChar == '\b' || keyChar == '0') {
                                // Usa Backspace o 0 per svuotare la cella
                                listener.onNumberInserted(r, c, 0);
                            }
                        }
                    }
                });

                cells[row][col] = btn;
                add(btn);
            }
        }
    }

    // --- METODI THREAD-SAFE PER L'AGGIORNAMENTO DALL'ESTERNO (RABBITMQ) ---

    public void updateCellValue(int row, int col, int value) {
        SwingUtilities.invokeLater(() -> {
            cells[row][col].setText(value == 0 ? "" : String.valueOf(value));
        });
    }

    public void updateCellSelection(int row, int col, String playerId, Color playerColor) {
        SwingUtilities.invokeLater(() -> {
            cells[row][col].setBackground(playerColor);
            cells[row][col].setToolTipText("Selected by: " + playerId);
        });
    }

    public void clearCellSelection(int row, int col) {
        SwingUtilities.invokeLater(() -> {
            cells[row][col].setBackground(Color.WHITE);
            cells[row][col].setToolTipText(null);
        });
    }

    public void display() {
        SwingUtilities.invokeLater(() -> setVisible(true));
    }
}
