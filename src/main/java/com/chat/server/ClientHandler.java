package com.chat.server;

import java.io.*;
import java.net.*;

public class ClientHandler implements Runnable {
    private Socket socket;
    private BufferedReader input;
    private PrintWriter output;
    private String username;
    private InetAddress ipAddress;
    private int udpPort = -1; // -1 means not registered for voice yet

    public ClientHandler(Socket socket) {
        this.socket = socket;
        this.ipAddress = socket.getInetAddress();
        try {
            this.input = new BufferedReader(new InputStreamReader(socket.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
            this.output = new PrintWriter(new java.io.OutputStreamWriter(socket.getOutputStream(), java.nio.charset.StandardCharsets.UTF_8), true);
        } catch (IOException e) {
            System.out.println("Error initializing streams: " + e.getMessage());
        }
    }

    public String getUsername() {
        return username;
    }

    public InetAddress getIpAddress() {
        return ipAddress;
    }

    public int getUdpPort() {
        return udpPort;
    }

    public void setUdpPort(int udpPort) {
        this.udpPort = udpPort;
    }

    public void setIpAddress(InetAddress ipAddress) {
        this.ipAddress = ipAddress;
    }

    @Override
    public void run() {
        try {
            // Get username from client
            output.println("Enter your username:");
            while (true) {
                username = input.readLine();
                if (username == null) return;
                username = username.trim();
                
                if (username.isEmpty()) {
                    output.println("Username cannot be empty. Try again:");
                } else if (!Server.registerClient(username, this)) {
                    output.println("Username already taken. Try another:");
                } else {
                    break;
                }
            }
            
            // Authentication successful
            output.println("/auth SUCCESS");
            Server.broadcastUserList();
            
            output.println("Welcome to the chat, " + username + "!");
            String joinMessage = "[SERVER] " + username + " has joined the chat";
            Server.broadcastMessage(joinMessage, username);
            
            // Listen for messages from this client
            String message;
            while ((message = input.readLine()) != null) {
                if (message.equals("exit")) {
                    break;
                }
                
                if (message.startsWith("/udpPort ")) {
                    // Client is registering their UDP port for audio
                    try {
                        this.udpPort = Integer.parseInt(message.substring(9).trim());
                        System.out.println(username + " registered UDP port: " + udpPort);
                    } catch (NumberFormatException e) {
                        System.out.println("Invalid UDP port from " + username);
                    }
                    continue;
                }
                
                if (message.startsWith("/msg ")) {
                    // Private message format: /msg targetUser actual message
                    String[] parts = message.split(" ", 3);
                    if (parts.length == 3) {
                        String targetUser = parts[1];
                        String actualMsg = parts[2];
                        Server.sendPrivateMessage(actualMsg, username, targetUser);
                    } else {
                        output.println("[Server]: Invalid format. Use /msg user message");
                    }
                } else if (!message.trim().isEmpty()) {
                    // Global message
                    String formattedMessage = username + ": " + message;
                    System.out.println(formattedMessage);
                    Server.broadcastMessage(formattedMessage, username);
                }
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
        if (username != null) {
            String leaveMessage = "[SERVER] " + username + " has left the chat";
            Server.broadcastMessage(leaveMessage, username);
            Server.removeClient(username);
        }
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            System.out.println("Error closing connection: " + e.getMessage());
        }
    }
}
