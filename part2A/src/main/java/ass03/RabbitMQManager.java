package ass03;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

public class RabbitMQManager {

    private static final String LOBBY_EXCHANGE = "sudoku_lobby";

    private Connection connection;
    private Channel channel;
    private final String playerId;
    private String currentQueueName;
    private String currentGameExchange;
    private final Map<String, Integer> vectorClock = new HashMap<>();

    public interface MessageListener {
        void onMessageReceived(String message);
    }

    // Listener interface to pass Lobby events to the Controller
    public interface LobbyListener {
        void onListGamesRequest(String requesterId);
        void onGameInfoReceived(String gameId, int playersCount);
    }

    public RabbitMQManager(String playerId) {
        this.playerId = playerId;
        this.vectorClock.put(playerId, 0);
    }

    public void connect(String host) throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(host);
        this.connection = factory.newConnection();
        this.channel = connection.createChannel();
    }

    public void disconnect() throws IOException, TimeoutException {
        if (channel != null && channel.isOpen()) {
            channel.close();
        }
        if (connection != null && connection.isOpen()) {
            connection.close();
        }
        System.out.println(" [" + playerId + "] Disconnected from RabbitMQ.");
    }

    // --- GAME PHASE (Sudoku) ---
    public void joinGame(String gameId, MessageListener listener) throws IOException {
        this.currentGameExchange = gameId;
        channel.exchangeDeclare(currentGameExchange, "fanout");
        currentQueueName = channel.queueDeclare().getQueue();
        channel.queueBind(currentQueueName, currentGameExchange, "");

        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
            listener.onMessageReceived(message);
        };

        channel.basicConsume(currentQueueName, true, deliverCallback, consumerTag -> { });
        System.out.println(" [" + playerId + "] Joined game: " + currentGameExchange);
    }

    public void leaveGame() throws IOException {
        if (currentQueueName != null) {
            channel.queueUnbind(currentQueueName, currentGameExchange, "");
            System.out.println(" [" + playerId + "] Left game: " + currentGameExchange);
        }
    }
    public void publishCellSelection(int row, int col) {
        incrementLocalClock();
        String message = String.format("SELECT|%s|%d|%d|%s", playerId, row, col, serializeClock());
        publishToExchange(currentGameExchange, message);
    }

    public void publishCellDeselection(int row, int col) {
        incrementLocalClock();
        String message = String.format("DESELECT|%s|%d|%d|%s", playerId, row, col, serializeClock());
        publishToExchange(currentGameExchange, message);
    }

    public void publishNumberInsertion(int row, int col, int value) {
        incrementLocalClock();
        String message = String.format("INSERT|%s|%d|%d|%d|%s", playerId, row, col, value, serializeClock());
        publishToExchange(currentGameExchange, message);
    }

    // --- LOBBY PHASE ---

    public void joinLobby(LobbyListener listener) throws IOException {
        channel.exchangeDeclare(LOBBY_EXCHANGE, "fanout");
        currentQueueName = channel.queueDeclare().getQueue();
        channel.queueBind(currentQueueName, LOBBY_EXCHANGE, "");

        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
            String[] parts = message.split("\\|");
            String action = parts[0];

            if ("LIST_GAMES".equals(action)) {
                String requesterId = parts[1];
                if (!requesterId.equals(this.playerId)) { // Ignore our own requests
                    listener.onListGamesRequest(requesterId);
                }
            } else if ("GAME_INFO".equals(action)) {
                String gameId = parts[1];
                int playersCount = Integer.parseInt(parts[2]);
                listener.onGameInfoReceived(gameId, playersCount);
            }
        };

        channel.basicConsume(currentQueueName, true, deliverCallback, consumerTag -> { });
        System.out.println(" [" + playerId + "] Joined lobby. Waiting for events...");
    }

    public void requestActiveGames() {
        String message = "LIST_GAMES|" + playerId;
        publishToExchange(LOBBY_EXCHANGE, message);
    }

    public void publishGameInfo(String gameId, int playersCount) {
        String message = "GAME_INFO|" + gameId + "|" + playersCount;
        publishToExchange(LOBBY_EXCHANGE, message);
    }

    public void publishStateRequest() {
        incrementLocalClock();
        String message = String.format("STATE_REQUEST|%s|%s", playerId, serializeClock());
        publishToExchange(currentGameExchange, message);
    }

    public void publishStateReply(String requesterId, String serializedBoard, String activePlayersStr) {
        incrementLocalClock();
        String message = String.format("STATE_REPLY|%s|%s|%s|%s|%s",
                playerId, requesterId, serializedBoard, activePlayersStr, serializeClock());
        publishToExchange(currentGameExchange, message);
    }

    private void publishToExchange(String exchangeName, String message) {
        if (channel != null && channel.isOpen()) {
            try {
                channel.basicPublish(exchangeName, "", null, message.getBytes(StandardCharsets.UTF_8));
                System.out.println(" [x] Sent to " + exchangeName + ": '" + message + "'");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // Leave the lobby before joining a specific game
    public void leaveLobby() throws IOException {
        if (currentQueueName != null) {
            channel.queueUnbind(currentQueueName, LOBBY_EXCHANGE, "");
            System.out.println(" [" + playerId + "] Left the lobby.");
        }
    }

    private void incrementLocalClock() {
        int current = vectorClock.getOrDefault(playerId, 0);
        vectorClock.put(playerId, current + 1);
    }

    //To include in timestamps
    private String serializeClock() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> entry : vectorClock.entrySet()) {
            sb.append(entry.getKey()).append(":").append(entry.getValue()).append(",");
        }
        return !sb.isEmpty() ? sb.substring(0, sb.length() - 1) : "";
    }

    public void updateClock(String receivedClockData) {
        if (receivedClockData == null || receivedClockData.isEmpty()) return;

        String[] entries = receivedClockData.split(",");
        for (String entry : entries) {
            String[] parts = entry.split(":");
            if (parts.length == 2) {
                String pId = parts[0];
                int msgClockVal = Integer.parseInt(parts[1]);

                // Update with max()
                int localClockVal = vectorClock.getOrDefault(pId, 0);
                vectorClock.put(pId, Math.max(localClockVal, msgClockVal));
            }
        }
    }
}