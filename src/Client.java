import java.io.*;
import java.net.*;

public class Client {
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 5000;
    private Socket socket;
    private BufferedReader input;
    private PrintWriter output;
    private BufferedReader userInput;

    public static void main(String[] args) {
        Client client = new Client();
        client.start();
    }

    public void start() {
        try {
            // Connect to server
            socket = new Socket(SERVER_HOST, SERVER_PORT);
            System.out.println("Connected to server at " + SERVER_HOST + ":" + SERVER_PORT);

            // Initialize streams
            input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            output = new PrintWriter(socket.getOutputStream(), true);
            userInput = new BufferedReader(new InputStreamReader(System.in));

            // Get username from user
            String username = null;
            String response = input.readLine(); // Server asks for username
            if (response != null) {
                System.out.println(response);
                username = userInput.readLine();
                output.println(username);
            }

            System.out.println("\nConnected as: " + username);
            System.out.println("Type messages below. Type 'exit' to quit.\n");

            // Start thread to receive messages
            new Thread(new ReceiveMessages()).start();

            // Send messages in main thread
            String message;
            while ((message = userInput.readLine()) != null) {
                if (message.equals("exit")) {
                    output.println("exit");
                    System.out.println("Disconnected from server");
                    break;
                }
                if (!message.trim().isEmpty()) {
                    output.println(message);
                }
            }

            closeConnection();
        } catch (UnknownHostException e) {
            System.out.println("Server not found at " + SERVER_HOST + ":" + SERVER_PORT);
        } catch (IOException e) {
            System.out.println("Connection error: " + e.getMessage());
        }
    }

    // Inner class to receive messages from server
    private class ReceiveMessages implements Runnable {
        @Override
        public void run() {
            try {
                String message;
                while ((message = input.readLine()) != null) {
                    System.out.println(message);
                }
            } catch (IOException e) {
                System.out.println("Connection closed");
            }
        }
    }

    private void closeConnection() {
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            System.out.println("Error closing connection: " + e.getMessage());
        }
    }
}
