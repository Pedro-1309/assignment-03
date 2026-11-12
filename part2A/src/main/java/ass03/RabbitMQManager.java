package ass03;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;

public class RabbitMQManager {

    private static final String EXCHANGE_NAME = "sudoku_exchange";

    private Connection connection;
    private Channel channel;
    private final String playerId;

    // Logical clock for the "happened-before" consistency
    private int[] vectorClock;

    // Interface to pass incoming messages to the Controller/View
    public interface MessageListener {
        void onMessageReceived(String message);
    }

    public RabbitMQManager(String playerId, int numberOfPlayers) {
        this.playerId = playerId;
        this.vectorClock = new int[numberOfPlayers]; // Piggybacked on outgoing messages
    }

    public void connect(String host) throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(host);

        this.connection = factory.newConnection();
        this.channel = connection.createChannel();

        // FANOUT exchange to broadcast all moves to every connected player
        channel.exchangeDeclare(EXCHANGE_NAME, "fanout");
    }

    public void startListening(MessageListener listener) throws IOException {
        // queue for this specific client
        String queueName = channel.queueDeclare().getQueue();

        // Bind the queue to the fanout exchange
        channel.queueBind(queueName, EXCHANGE_NAME, "");

        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
            listener.onMessageReceived(message);
        };

        // Start consuming messages asynchronously
        channel.basicConsume(queueName, true, deliverCallback, consumerTag -> { });
        System.out.println(" [*] Waiting for messages...");
    }

    public void publishCellSelection(int row, int col) {
        // Increment the local clock component before sending the request
        incrementLocalClock();

        String message = String.format("SELECT|%s|%d|%d|%s", playerId, row, col, serializeClock());
        publishMessage(message);
    }

    public void publishNumberInsertion(int row, int col, int value) {
        // Increment the local clock component before sending the request
        incrementLocalClock();

        String message = String.format("INSERT|%s|%d|%d|%d|%s", playerId, row, col, value, serializeClock());
        publishMessage(message);
    }

    public void publishCellDeselection(int row, int col) {
        incrementLocalClock();
        String message = String.format("DESELECT|%s|%d|%d|%s", playerId, row, col, serializeClock());
        publishMessage(message);
    }

    private void publishMessage(String message) {
        if (channel != null && channel.isOpen()) {
            try {
                // empty routing key for fanout
                channel.basicPublish(EXCHANGE_NAME, "", null, message.getBytes(StandardCharsets.UTF_8));
                System.out.println(" [x] Sent '" + message + "'");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void incrementLocalClock() {
        // Assuming this player has a specific index in the array for the vector clock.
        // For a fully decentralized dynamic network, a Map<String, Integer> might be used instead.
        // vectorClock[myIndex]++;
    }

    private String serializeClock() {
        // Convert the vector clock array to a string representation for piggybacking
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < vectorClock.length; i++) {
            sb.append(vectorClock[i]).append(i < vectorClock.length - 1 ? "," : "");
        }
        return sb.toString();
    }

    public void disconnect() {
        try {
            if (channel != null && channel.isOpen()) channel.close();
            if (connection != null && connection.isOpen()) connection.close();
        } catch (IOException | TimeoutException e) {
            e.printStackTrace();
        }
    }
}