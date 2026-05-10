import java.io.*;
import java.net.*;

public class ClientHandler implements Runnable {
    private Socket socket;
    private BufferedReader input;
    private PrintWriter output;
    private String username;

    public ClientHandler(Socket socket) {
        this.socket = socket;
        try {
            this.input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.output = new PrintWriter(socket.getOutputStream(), true);
        } catch (IOException e) {
            System.out.println("Error initializing streams: " + e.getMessage());
        }
    }

    @Override
    public void run() {
        try {
            // Get username from client
            output.println("Enter your username:");
            username = input.readLine();
            
            if (username != null && !username.trim().isEmpty()) {
                System.out.println(username + " joined the chat");
                
                // Notify all clients about new user
                String joinMessage = "[SERVER] " + username + " has joined the chat";
                Server.broadcastMessage(joinMessage, this);
                
                // Listen for messages from this client
                String message;
                while ((message = input.readLine()) != null && !message.equals("exit")) {
                    if (!message.trim().isEmpty()) {
                        String formattedMessage = username + ": " + message;
                        System.out.println(formattedMessage);
                        Server.broadcastMessage(formattedMessage, this);
                    }
                }
                
                // Handle disconnection
                String leaveMessage = "[SERVER] " + username + " has left the chat";
                System.out.println(username + " disconnected");
                Server.broadcastMessage(leaveMessage, this);
            }
        } catch (IOException e) {
            System.out.println("Error with client connection: " + e.getMessage());
        } finally {
            closeConnection();
        }
    }

    // Send message to this client
    public void sendMessage(String message) {
        output.println(message);
    }

    // Close connection and remove from server
    private void closeConnection() {
        try {
            if (socket != null) {
                socket.close();
            }
            Server.removeClient(this);
        } catch (IOException e) {
            System.out.println("Error closing connection: " + e.getMessage());
        }
    }
}
