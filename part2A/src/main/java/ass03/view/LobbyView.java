package ass03.view;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class LobbyView extends JFrame {

    private final DefaultListModel<String> gamesModel;
    private final JList<String> gamesList;
    private LobbyActionListener listener;

    // Delegate user actions to the Controller (Main)
    public interface LobbyActionListener {
        void onJoinGame(String gameId);
        void onCreateNewGame();
        void onExit();
    }

    public LobbyView() {
        setTitle("Cooperative Sudoku - Lobby");
        setSize(400, 300);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
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

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (listener != null) {
                    listener.onExit();
                } else {
                    System.exit(0);
                }
            }
        });
    }

    public void setListener(LobbyActionListener listener) {
        this.listener = listener;
    }

    // Called from the RabbitMQ network thread: use invokeLater to update the List safely
    public void addAvailableGame(String gameId, int playersCount) {
        SwingUtilities.invokeLater(() -> {
            String displayString = gameId + " (" + playersCount + " players)";
            String prefix = gameId + " ";

            // Check if the game already exists
            for (int i = 0; i < gamesModel.size(); i++) {
                String item = gamesModel.getElementAt(i);
                if (item.startsWith(prefix)) {
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

    public void removeAvailableGame(String gameId) {
        SwingUtilities.invokeLater(() -> {
            String prefix = gameId + " ";
            for (int i = 0; i < gamesModel.size(); i++) {
                String item = gamesModel.getElementAt(i);
                if (item.startsWith(prefix)) {
                    gamesModel.remove(i);
                    break;
                }
            }
        });
    }

    public void showInfoDialog(String message, String title) {
        SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(this, message, title, JOptionPane.INFORMATION_MESSAGE)
        );
    }

    public void display() {
        SwingUtilities.invokeLater(() -> setVisible(true));
    }

    public void hideView() {
        SwingUtilities.invokeLater(() -> setVisible(false));
    }
}
