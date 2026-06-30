package com.chat.server;

import java.io.*;
import java.net.*;

public class ClientHandler implements Runnable {
    // Protocol handshake: client must send this exact token as the first line
    private static final String HANDSHAKE_TOKEN = "CHAT_HELLO_v1";
    // Time (ms) allowed for a client to complete the handshake before being dropped
    private static final int HANDSHAKE_TIMEOUT_MS = 15000;
    // Max allowed username length
    private static final int MAX_USERNAME_LEN = 32;

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
            // Set socket timeout for handshake phase — bots/scanners will be dropped
            socket.setSoTimeout(HANDSHAKE_TIMEOUT_MS);
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
            // ── STEP 1: Protocol Handshake ────────────────────────────────────────
            // The very first line MUST be exactly "CHAT_HELLO_v1".
            // Bots/HTTP scanners will never send this, so they get silently dropped.
            String handshake = input.readLine();
            if (handshake == null || !handshake.trim().equals(HANDSHAKE_TOKEN)) {
                // Silent drop — don't log or respond, to avoid feeding scanner info
                silentClose();
                return;
            }
            output.println("CHAT_HELLO_ACK");

            // ── STEP 2: Username negotiation ──────────────────────────────────────
            output.println("Enter your username:");
            while (true) {
                username = input.readLine();
                if (username == null) return;
                username = username.trim();

                if (username.isEmpty()) {
                    output.println("Username cannot be empty. Try again:");
                } else if (username.length() > MAX_USERNAME_LEN) {
                    output.println("Username too long (max " + MAX_USERNAME_LEN + " chars). Try again:");
                } else if (!username.matches("[a-zA-Z0-9_\\-]+")) {
                    output.println("Username may only contain letters, numbers, _ and -. Try again:");
                } else if (!Server.registerClient(username, this)) {
                    output.println("Username already taken. Try another:");
                } else {
                    break;
                }
            }

            // ── STEP 3: Post-auth — remove the handshake timeout, use a longer one ─
            // After a legitimate user is authenticated, switch to a generous read
            // timeout so idle real users aren't disconnected, but dead sockets are.
            socket.setSoTimeout(300_000); // 5 minutes

            output.println("/auth SUCCESS");
            Server.broadcastUserList();

            output.println("Welcome to the chat, " + username + "!");
            String joinMessage = "[SERVER] " + username + " has joined the chat";
            Server.broadcastMessage(joinMessage, username);

            // ── STEP 4: Message loop ───────────────────────────────────────────────
            String message;
            while ((message = input.readLine()) != null) {
                if (message.equals("exit")) {
                    break;
                }

                if (message.startsWith("/udpPort ")) {
                    try {
                        this.udpPort = Integer.parseInt(message.substring(9).trim());
                        System.out.println(username + " registered UDP port: " + udpPort);
                    } catch (NumberFormatException e) {
                        System.out.println("Invalid UDP port from " + username);
                    }
                    continue;
                }

                if (message.startsWith("/msg ")) {
                    String[] parts = message.split(" ", 3);
                    if (parts.length == 3) {
                        String targetUser = parts[1];
                        String actualMsg = parts[2];
                        Server.sendPrivateMessage(actualMsg, username, targetUser);
                    } else {
                        output.println("[Server]: Invalid format. Use /msg user message");
                    }
                } else if (!message.trim().isEmpty()) {
                    String formattedMessage = username + ": " + message;
                    System.out.println(formattedMessage);
                    Server.broadcastMessage(formattedMessage, username);
                }
            }

        } catch (SocketTimeoutException e) {
            // Handshake or keep-alive timed out — silently drop the connection
            System.out.println("Connection timed out from " + ipAddress.getHostAddress() + " (bot/scanner likely)");
        } catch (IOException e) {
            if (username != null) {
                System.out.println("Error with client " + username + ": " + e.getMessage());
            }
        } finally {
            closeConnection();
        }
    }

    // Send message to this client
    public void sendMessage(String message) {
        output.println(message);
    }

    // Silent close — no response sent (used for rejected bot connections)
    private void silentClose() {
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {}
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
