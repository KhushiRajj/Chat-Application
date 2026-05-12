package com.chat.client;

import javax.sound.sampled.*;
import java.io.IOException;
import java.net.*;

public class AudioClient {
    private static final int SERVER_PORT = 5001; // UDP Port
    private DatagramSocket udpSocket;
    private TargetDataLine microphone;
    private SourceDataLine speaker;
    private boolean isCallActive = false;
    
    // Audio Format: 16000 Hz, 16 bit, Mono, Signed, Little-Endian
    private AudioFormat format = new AudioFormat(16000.0f, 16, 1, true, false);

    public AudioClient() {
        try {
            udpSocket = new DatagramSocket(); // Bind to any available local port
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }

    public int getLocalPort() {
        return udpSocket != null ? udpSocket.getLocalPort() : -1;
    }

    public void startCall(String serverIp) {
        if (isCallActive) return;
        isCallActive = true;

        try {
            // Setup Microphone with small buffer for low latency
            DataLine.Info micInfo = new DataLine.Info(TargetDataLine.class, format);
            microphone = (TargetDataLine) AudioSystem.getLine(micInfo);
            microphone.open(format, 1024);
            microphone.start();

            // Setup Speaker with small buffer
            DataLine.Info speakerInfo = new DataLine.Info(SourceDataLine.class, format);
            speaker = (SourceDataLine) AudioSystem.getLine(speakerInfo);
            speaker.open(format, 1024);
            speaker.start();

            // Thread to capture and send audio
            Thread captureThread = new Thread(() -> {
                byte[] buffer = new byte[512]; // Smaller packet size
                try {
                    InetAddress serverAddress = InetAddress.getByName(serverIp);
                    while (isCallActive) {
                        int bytesRead = microphone.read(buffer, 0, buffer.length);
                        if (bytesRead > 0) {
                            DatagramPacket sendPacket = new DatagramPacket(buffer, bytesRead, serverAddress, SERVER_PORT);
                            udpSocket.send(sendPacket);
                        }
                    }
                } catch (Exception e) {
                    if (isCallActive) e.printStackTrace();
                }
            });

            // Thread to receive and play audio
            Thread playThread = new Thread(() -> {
                byte[] buffer = new byte[512]; // Match capture packet size
                try {
                    while (isCallActive) {
                        DatagramPacket receivePacket = new DatagramPacket(buffer, buffer.length);
                        udpSocket.receive(receivePacket);
                        speaker.write(receivePacket.getData(), 0, receivePacket.getLength());
                    }
                } catch (Exception e) {
                    if (isCallActive) e.printStackTrace();
                }
            });

            captureThread.setDaemon(true);
            playThread.setDaemon(true);
            captureThread.start();
            playThread.start();

            System.out.println("Audio call started.");
        } catch (LineUnavailableException e) {
            System.err.println("Audio line unavailable. Make sure you have a microphone and speaker.");
            isCallActive = false;
        }
    }

    public void stopCall() {
        isCallActive = false;
        if (microphone != null) {
            microphone.stop();
            microphone.close();
        }
        if (speaker != null) {
            speaker.stop();
            speaker.close();
        }
        System.out.println("Audio call stopped.");
    }
}
