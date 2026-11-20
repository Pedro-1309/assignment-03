package ass03.server;

import ass03.common.SudokuService;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class SudokuServer {
    public static void main(String[] args) {
        try {
            SudokuService sudokuService = new SudokuServiceImpl();

            Registry registry = LocateRegistry.createRegistry(1099);

            // Publish remote object in the registry
            registry.rebind("SudokuService", sudokuService);

            System.out.println("Server RMI Sudoku started. Waiting for connections...");

        } catch (Exception e) {
            System.err.println("Error starting RMI server.");
            e.printStackTrace();
        }
    }
}
