package ass03;

import ass03.controller.GameController;

public class Main {
    public static void main(String[] args) {
        String host = (args.length > 0) ? args[0] : "localhost";
        new GameController(host);
    }
}