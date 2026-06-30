package com.chat.client;

import java.io.*;
import java.net.*;
import java.util.function.Consumer;

public class ChatClient {
    private static final int SERVER_PORT = 5000;
    
    private Socket socket;
    private BufferedReader input;
    private PrintWriter output;
    private boolean isConnected = false;
    
    private Consumer<String> onMessageReceived;
    private Consumer<String[]> onUserListUpdated;

    public void setOnMessageReceived(Consumer<String> callback) {
        this.onMessageReceived = callback;
    }
    
    public void setOnUserListUpdated(Consumer<String[]> callback) {
        this.onUserListUpdated = callback;
    }

    public boolean connect(String serverIp, String username, int audioPort) {
        try {
            socket = new Socket(serverIp, SERVER_PORT);
            socket.setSoTimeout(10_000); // 10 second connect timeout
            input = new BufferedReader(new InputStreamReader(socket.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
            output = new PrintWriter(new java.io.OutputStreamWriter(socket.getOutputStream(), java.nio.charset.StandardCharsets.UTF_8), true);
            isConnected = true;

            // ── Handshake: must be first line sent ──
            output.println("CHAT_HELLO_v1");
            String ack = input.readLine();
            if (!"CHAT_HELLO_ACK".equals(ack)) {
                close();
                return false;
            }

            // Wait for "Enter your username:"
            input.readLine();
            output.println(username); // send username

            // Wait for auth confirmation
            while (true) {
                String response = input.readLine();
                if (response == null) return false;
                if (response.startsWith("Username already taken") || response.startsWith("Username cannot be empty")) {
                    close();
                    return false;
                }
                if (response.equals("/auth SUCCESS")) {
                    break;
                }
            }

            // Remove the connect-phase timeout; the listener handles keep-alive
            socket.setSoTimeout(0);

            // Register UDP port for audio
            output.println("/udpPort " + audioPort);

            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }


    public void startListening() {
        if (isConnected) {
            Thread listenThread = new Thread(this::listenForMessages);
            listenThread.setDaemon(true);
            listenThread.start();
        }
    }

    private void listenForMessages() {
        try {
            String message;
            while (isConnected && (message = input.readLine()) != null) {
                if (message.startsWith("/users ")) {
                    String userString = message.substring(7);
                    if (onUserListUpdated != null) {
                        String[] users = userString.split(",");
                        onUserListUpdated.accept(users);
                    }
                } else {
                    if (onMessageReceived != null) {
                        onMessageReceived.accept(message);
                    }
                }
            }
        } catch (IOException e) {
            if (isConnected) {
                if (onMessageReceived != null) {
                    onMessageReceived.accept("Disconnected from server.");
                }
            }
        } finally {
            close();
        }
    }

    public void sendMessage(String message) {
        if (output != null && isConnected) {
            output.println(message);
        }
    }

    public void sendPrivateMessage(String targetUser, String message) {
        if (output != null && isConnected) {
            output.println("/msg " + targetUser + " " + message);
        }
    }

    public void close() {
        isConnected = false;
        try {
            if (socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
