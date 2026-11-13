package ass03;

import javax.swing.*;
import java.awt.*;

public class LobbyView extends JFrame {

    private final DefaultListModel<String> gamesModel;
    private final JList<String> gamesList;
    private LobbyActionListener listener;

    // Delegate user actions to the Controller (Main)
    public interface LobbyActionListener {
        void onJoinGame(String gameId);
        void onCreateNewGame();
    }

    public LobbyView() {
        setTitle("Sudoku Cooperative - Lobby");
        setSize(400, 300);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        gamesModel = new DefaultListModel<>();
        gamesList = new JList<>(gamesModel);
        gamesList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JScrollPane scrollPane = new JScrollPane(gamesList);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Active Games on Network"));
        add(scrollPane, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel();
        JButton btnJoin = new JButton("Join Selected Game");
        JButton btnCreate = new JButton("Create New Game");

        btnJoin.addActionListener(e -> {
            String selected = gamesList.getSelectedValue();
            if (selected != null && listener != null) {
                String gameId = selected.split(" ")[0];
                listener.onJoinGame(gameId);
            } else {
                JOptionPane.showMessageDialog(this, "Please select a game first!");
            }
        });

        btnCreate.addActionListener(e -> {
            if (listener != null) {
                listener.onCreateNewGame();
            }
        });

        buttonPanel.add(btnJoin);
        buttonPanel.add(btnCreate);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    public void setListener(LobbyActionListener listener) {
        this.listener = listener;
    }

    // Called from the RabbitMQ network thread: use invokeLater to update the List safely
    public void addAvailableGame(String gameId, int playersCount) {
        SwingUtilities.invokeLater(() -> {
            String displayString = gameId + " (" + playersCount + " players)";

            // Check if the game already exists
            for (int i = 0; i < gamesModel.size(); i++) {
                String item = gamesModel.getElementAt(i);
                if (item.startsWith(gameId)) {
                    if (!item.equals(displayString)) {
                        // Same id, different number of players: update the view
                        gamesModel.set(i, displayString);
                    }
                    return;
                }
            }
            // If not found, create a new one
            gamesModel.addElement(displayString);
        });
    }

    public void display() {
        SwingUtilities.invokeLater(() -> setVisible(true));
    }

    public void hideView() {
        SwingUtilities.invokeLater(() -> {
            setVisible(false);
            dispose();
        });
    }
}
