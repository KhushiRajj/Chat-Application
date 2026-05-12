package com.chat.server;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Server {
    private static final int PORT = 5000;
    private static final int UDP_PORT = 5001; // Port for audio routing
    
    private static ServerSocket serverSocket;
    private static DatagramSocket udpSocket;
    
    // Map to keep track of username to ClientHandler for private messaging
    private static Map<String, ClientHandler> clients = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        try {
            serverSocket = new ServerSocket(PORT);
            udpSocket = new DatagramSocket(UDP_PORT);
            
            System.out.println("Server started on TCP port " + PORT + " and UDP port " + UDP_PORT);
            System.out.println("Waiting for clients...\n");

            // Start UDP listener thread for audio
            new Thread(Server::listenForAudio).start();

            // Accept client connections indefinitely
            while (true) {
                Socket clientSocket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(clientSocket);
                new Thread(handler).start();
            }
        } catch (IOException e) {
            System.out.println("Server error: " + e.getMessage());
        }
    }

    // Register a client after they provide a username
    public static synchronized boolean registerClient(String username, ClientHandler handler) {
        if (clients.containsKey(username)) {
            return false; // Username already taken
        }
        clients.put(username, handler);
        System.out.println(username + " joined. Total clients: " + clients.size());
        return true;
    }

    // Broadcast message to all connected clients
    public static synchronized void broadcastMessage(String message, String senderUsername) {
        for (ClientHandler client : clients.values()) {
            if (!client.getUsername().equals(senderUsername)) {
                client.sendMessage(message);
            }
        }
    }

    // Send a private message to a specific user
    public static synchronized void sendPrivateMessage(String message, String senderUsername, String targetUsername) {
        ClientHandler target = clients.get(targetUsername);
        if (target != null) {
            target.sendMessage("[Private from " + senderUsername + "]: " + message);
        } else {
            ClientHandler sender = clients.get(senderUsername);
            if (sender != null) {
                sender.sendMessage("[Server]: User " + targetUsername + " not found or offline.");
            }
        }
    }

    // Broadcast the updated user list to everyone
    public static synchronized void broadcastUserList() {
        StringBuilder userListMessage = new StringBuilder("/users ");
        for (String user : clients.keySet()) {
            userListMessage.append(user).append(",");
        }
        
        for (ClientHandler client : clients.values()) {
            client.sendMessage(userListMessage.toString());
        }
    }

    // Remove client from list when they disconnect
    public static synchronized void removeClient(String username) {
        if (username != null && clients.remove(username) != null) {
            System.out.println(username + " disconnected. Total clients: " + clients.size());
            broadcastUserList();
        }
    }

    // --- UDP AUDIO ROUTING ---
    private static void listenForAudio() {
        byte[] buffer = new byte[4096]; // Standard buffer for audio packets
        try {
            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                udpSocket.receive(packet);

                // For a peer-to-peer call, we need a way to know who this packet belongs to and who it goes to.
                // In a simple group voice chat, we just broadcast it to everyone else.
                // We'll extract the sender's IP/Port from the packet and send to others.
                
                // Currently, we will just broadcast audio to all active clients for group voice chat.
                // Or if we want End-to-End, we'd need the client to prepend the target username to the packet.
                // For simplicity first: Broadcast voice to all.
                
                for (ClientHandler client : clients.values()) {
                    // Make sure we know the client's UDP port if we want to send it back.
                    // For now, if the client has established a UDP connection and we know their Datagram port.
                    // To do this properly, the client must first send a UDP packet to register their IP/port,
                    // or we rely on the TCP IP and a predefined port.
                    
                    if (client.getUdpPort() != -1 && 
                        !(packet.getAddress().equals(client.getIpAddress()) && packet.getPort() == client.getUdpPort())) {
                        
                        DatagramPacket sendPacket = new DatagramPacket(
                            packet.getData(), packet.getLength(), 
                            client.getIpAddress(), client.getUdpPort()
                        );
                        udpSocket.send(sendPacket);
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("UDP Server Error: " + e.getMessage());
        }
    }
}
