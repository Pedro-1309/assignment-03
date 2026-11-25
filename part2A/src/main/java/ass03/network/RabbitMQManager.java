package ass03.network;

import ass03.model.VectorClock;
import com.rabbitmq.client.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;

public class RabbitMQManager {

    private static final String LOBBY_EXCHANGE = "sudoku_lobby";
    private static final String LOBBY_GAMES_QUEUE = "sudoku_lobby_games";

    private final String playerId;
    private final VectorClock vectorClock;
    private final Object channelLock = new Object();

    private Connection connection;
    private Channel channel;

    private String lobbyQueueName;
    private String lobbyConsumerTag;
    private String currentGameExchange;
    private String gameQueueName;
    private String gameConsumerTag;

    public interface MessageListener {
        void onMessageReceived(String message);
    }

    public interface LobbyListener {
        void onListGamesRequest(String requesterId);
        void onGameInfoReceived(String gameId, int playersCount);
    }

    public RabbitMQManager(String playerId) {
        this.playerId = playerId;
        this.vectorClock = new VectorClock(playerId);
    }

    public void connect(String host) throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(host);
        synchronized (channelLock) {
            this.connection = factory.newConnection();
            this.channel = connection.createChannel();
        }
    }

    public void disconnect() {
        synchronized (channelLock) {
            try {
                leaveGame();
                if (channel != null && channel.isOpen()) {
                    if (lobbyConsumerTag != null) {
                        channel.basicCancel(lobbyConsumerTag);
                        lobbyConsumerTag = null;
                    }
                    if (lobbyQueueName != null) {
                        channel.queueDelete(lobbyQueueName);
                        lobbyQueueName = null;
                    }
                    channel.close();
                }
                if (connection != null && connection.isOpen()) {
                    connection.close();
                }
                System.out.println(" [" + playerId + "] Disconnected from RabbitMQ.");
            } catch (IOException | TimeoutException e) {
                System.err.println(" [" + playerId + "] Error during disconnect: " + e.getMessage());
            }
        }
    }

    public void updateClock(String receivedClockData) {
        vectorClock.update(receivedClockData);
    }

    public void resetClock() {
        vectorClock.reset(playerId);
    }

    // --- LOBBY ---

    public void joinLobby(LobbyListener listener) throws IOException {
        synchronized (channelLock) {
            channel.exchangeDeclare(LOBBY_EXCHANGE, "fanout");
            lobbyQueueName = channel.queueDeclare().getQueue();
            channel.queueBind(lobbyQueueName, LOBBY_EXCHANGE, "");

            DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
                String[] parts = message.split("\\|");
                String action = parts[0];

                if ("LIST_GAMES".equals(action)) {
                    String requesterId = parts[1];
                    if (!requesterId.equals(this.playerId)) {
                        listener.onListGamesRequest(requesterId);
                    }
                } else if ("GAME_INFO".equals(action)) {
                    String gameId = parts[1];
                    int playersCount = Integer.parseInt(parts[2]);
                    listener.onGameInfoReceived(gameId, playersCount);
                }
            };

            lobbyConsumerTag = channel.basicConsume(lobbyQueueName, true, deliverCallback, consumerTag -> {});
        }
        System.out.println(" [" + playerId + "] Joined lobby. Waiting for events...");
    }

    public void requestActiveGames() {
        publishToExchange(LOBBY_EXCHANGE, "LIST_GAMES|" + playerId);
    }

    public void publishGameInfo(String gameId, int playersCount) {
        publishToExchange(LOBBY_EXCHANGE, "GAME_INFO|" + gameId + "|" + playersCount);
    }

    // --- GAME ---

    public void joinGame(String gameId, MessageListener listener) throws IOException {
        synchronized (channelLock) {
            // Ensure any previous game subscription is cleaned up
            if (currentGameExchange != null) {
                leaveGame();
            }

            this.currentGameExchange = gameId;
            channel.exchangeDeclare(currentGameExchange, "fanout");
            gameQueueName = channel.queueDeclare().getQueue();
            channel.queueBind(gameQueueName, currentGameExchange, "");

            DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
                listener.onMessageReceived(message);
            };

            gameConsumerTag = channel.basicConsume(gameQueueName, true, deliverCallback, consumerTag -> {});
        }
        System.out.println(" [" + playerId + "] Joined game: " + gameId);
    }

    public void leaveGame() throws IOException {
        String leftExchange;
        synchronized (channelLock) {
            leftExchange = currentGameExchange;
            if (channel != null && channel.isOpen()) {
                if (gameConsumerTag != null) {
                    channel.basicCancel(gameConsumerTag);
                }
                if (gameQueueName != null) {
                    if (currentGameExchange != null) {
                        channel.queueUnbind(gameQueueName, currentGameExchange, "");
                    }
                    channel.queueDelete(gameQueueName);
                }
            }
            gameConsumerTag = null;
            gameQueueName = null;
            currentGameExchange = null;
        }
        if (leftExchange != null) {
            System.out.println(" [" + playerId + "] Left the game exchange: " + leftExchange);
        }
    }

    public void publishJoin() {
        String clock = vectorClock.incrementAndSerialize(playerId);
        publishToCurrentGame(String.format("JOIN|%s|%s", playerId, clock));
    }

    public void publishSyncPlayers(String targetId, String activePlayersStr) {
        String clock = vectorClock.incrementAndSerialize(playerId);
        publishToCurrentGame(String.format("SYNC_PLAYERS|%s|%s|%s|%s", playerId, targetId, activePlayersStr, clock));
    }

    public void publishCellSelection(int row, int col) {
        String clock = vectorClock.incrementAndSerialize(playerId);
        publishToCurrentGame(String.format("SELECT|%s|%d|%d|%s", playerId, row, col, clock));
    }

    public void publishCellDeselection(int row, int col) {
        String clock = vectorClock.incrementAndSerialize(playerId);
        publishToCurrentGame(String.format("DESELECT|%s|%d|%d|%s", playerId, row, col, clock));
    }

    public void publishNumberInsertion(int row, int col, int value) {
        String clock = vectorClock.incrementAndSerialize(playerId);
        publishToCurrentGame(String.format("INSERT|%s|%d|%d|%d|%s", playerId, row, col, value, clock));
    }

    public void publishQuit() {
        String clock = vectorClock.incrementAndSerialize(playerId);
        publishToCurrentGame(String.format("QUIT|%s|%s", playerId, clock));
    }

    public void publishGameOver() {
        String clock = vectorClock.incrementAndSerialize(playerId);
        publishToCurrentGame(String.format("GAME_OVER|%s|%s", playerId, clock));
    }

    public void publishPing() {
        publishToCurrentGame(String.format("PING|%s", playerId));
    }

    private void publishToCurrentGame(String message) {
        synchronized (channelLock) {
            if (currentGameExchange != null) {
                publishToExchangeInternal(currentGameExchange, message);
            }
        }
    }

    private void publishToExchange(String exchangeName, String message) {
        synchronized (channelLock) {
            publishToExchangeInternal(exchangeName, message);
        }
    }

    private void publishToExchangeInternal(String exchangeName, String message) {
        if (channel != null && channel.isOpen() && exchangeName != null) {
            try {
                channel.basicPublish(exchangeName, "", null, message.getBytes(StandardCharsets.UTF_8));
                System.out.println(" [x] Sent to " + exchangeName + ": '" + message + "'");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // --- PERSISTENT RETAINED STATE ---

    public void retainGameState(String gameId, String serializedState) throws IOException {
        String stateQueue = gameId + "_STATE";
        Map<String, Object> args = Map.of("x-max-length", 1);

        synchronized (channelLock) {
            channel.queueDeclare(stateQueue, true, false, false, args);
            channel.basicPublish("", stateQueue, MessageProperties.PERSISTENT_TEXT_PLAIN,
                    serializedState.getBytes(StandardCharsets.UTF_8));
        }
    }

    public String getRetainedGameState(String gameId) throws IOException {
        String stateQueue = gameId + "_STATE";
        Map<String, Object> args = Map.of("x-max-length", 1);

        synchronized (channelLock) {
            channel.queueDeclare(stateQueue, true, false, false, args);
            GetResponse response = channel.basicGet(stateQueue, false);
            if (response != null) {
                String state = new String(response.getBody(), StandardCharsets.UTF_8);
                channel.basicReject(response.getEnvelope().getDeliveryTag(), true);
                return state;
            }
        }
        return "EMPTY";
    }

    public void deleteRetainedGameState(String gameId) {
        String stateQueue = gameId + "_STATE";
        synchronized (channelLock) {
            try {
                channel.queueDelete(stateQueue);
            } catch (IOException e) {
                System.err.println("Error deleting state queue: " + e.getMessage());
            }
        }
    }

    // --- PERSISTENT LOBBY STATE (for 0-player games) ---

    public Set<String> getRetainedLobbyGames() throws IOException {
        Set<String> games = new LinkedHashSet<>();
        Map<String, Object> args = Map.of("x-max-length", 1);

        synchronized (channelLock) {
            channel.queueDeclare(LOBBY_GAMES_QUEUE, true, false, false, args);
            GetResponse response = channel.basicGet(LOBBY_GAMES_QUEUE, false);
            if (response != null) {
                String payload = new String(response.getBody(), StandardCharsets.UTF_8);
                channel.basicReject(response.getEnvelope().getDeliveryTag(), true);
                if (!payload.isEmpty() && !"EMPTY".equals(payload)) {
                    for (String id : payload.split(",")) {
                        if (!id.isEmpty()) {
                            games.add(id);
                        }
                    }
                }
            }
        }
        return games;
    }

    public void addRetainedLobbyGame(String gameId) throws IOException {
        synchronized (channelLock) {
            Set<String> games = getRetainedLobbyGames();
            if (games.add(gameId)) {
                saveRetainedLobbyGames(games);
            }
        }
    }

    public void removeRetainedLobbyGame(String gameId) throws IOException {
        synchronized (channelLock) {
            Set<String> games = getRetainedLobbyGames();
            if (games.remove(gameId)) {
                saveRetainedLobbyGames(games);
            }
        }
    }

    private void saveRetainedLobbyGames(Set<String> games) throws IOException {
        Map<String, Object> args = Map.of("x-max-length", 1);
        String payload = games.isEmpty() ? "EMPTY" : String.join(",", games);
        channel.queueDeclare(LOBBY_GAMES_QUEUE, true, false, false, args);
        channel.basicPublish("", LOBBY_GAMES_QUEUE, MessageProperties.PERSISTENT_TEXT_PLAIN,
                payload.getBytes(StandardCharsets.UTF_8));
    }
}