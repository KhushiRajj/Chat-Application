package com.chat.client;

import javax.sound.sampled.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;

public class AudioClient {
    private static final int SERVER_PORT = 5001; // UDP Port
    private DatagramSocket udpSocket;
    private TargetDataLine microphone;
    private SourceDataLine speaker;
    private boolean isCallActive = false;

    // Audio Format: 16000 Hz, 16 bit, Mono, Signed, Little-Endian
    private final AudioFormat format = new AudioFormat(16000.0f, 16, 1, true, false);

    private String selectedMicrophoneName = null;

    /**
     * Set the microphone device to use (by mixer name). Null = system default.
     * If a call is currently active, the mic switches live without restarting the call.
     */
    public void setMicrophone(String name) {
        this.selectedMicrophoneName = name;
        System.out.println("[AudioClient] Mic preference set to: " + (name != null ? name : "System Default"));
    }

    /**
     * Returns a live list of all available input (microphone) device names.
     * Queries the system fresh each time — picks up newly connected devices.
     */
    public static List<String> getAvailableMicrophones() {
        List<String> mics = new ArrayList<>();
        AudioFormat probeFormat = new AudioFormat(16000.0f, 16, 1, true, false);
        DataLine.Info targetInfo = new DataLine.Info(TargetDataLine.class, probeFormat);
        for (Mixer.Info mixerInfo : AudioSystem.getMixerInfo()) {
            try {
                Mixer mixer = AudioSystem.getMixer(mixerInfo);
                if (mixer.isLineSupported(targetInfo)) {
                    mics.add(mixerInfo.getName());
                }
            } catch (Exception ignored) {}
        }
        return mics;
    }

    public AudioClient() {
        try {
            udpSocket = new DatagramSocket();
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }

    public int getLocalPort() {
        return udpSocket != null ? udpSocket.getLocalPort() : -1;
    }

    /**
     * Opens the best available TargetDataLine.
     * Priority: selectedMicrophoneName → first working mic → system default.
     */
    private TargetDataLine openBestMicLine() throws LineUnavailableException {
        DataLine.Info micInfo = new DataLine.Info(TargetDataLine.class, format);

        // 1. Try user-selected device
        if (selectedMicrophoneName != null) {
            for (Mixer.Info info : AudioSystem.getMixerInfo()) {
                if (info.getName().equals(selectedMicrophoneName)) {
                    try {
                        Mixer mixer = AudioSystem.getMixer(info);
                        TargetDataLine line = (TargetDataLine) mixer.getLine(micInfo);
                        line.open(format, 1024);
                        System.out.println("[AudioClient] Using selected mic: " + info.getName());
                        return line;
                    } catch (Exception e) {
                        System.err.println("[AudioClient] Selected mic unavailable: " + info.getName() + " — " + e.getMessage());
                    }
                }
            }
            System.out.println("[AudioClient] Selected mic not found, trying all available devices...");
        }

        // 2. Try every available mixer
        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            try {
                Mixer mixer = AudioSystem.getMixer(info);
                if (!mixer.isLineSupported(micInfo)) continue;
                TargetDataLine line = (TargetDataLine) mixer.getLine(micInfo);
                line.open(format, 1024);
                System.out.println("[AudioClient] Auto-selected mic: " + info.getName());
                return line;
            } catch (Exception ignored) {}
        }

        // 3. System default fallback
        if (!AudioSystem.isLineSupported(micInfo)) {
            throw new LineUnavailableException("No compatible microphone found on this system.");
        }
        TargetDataLine line = (TargetDataLine) AudioSystem.getLine(micInfo);
        line.open(format, 1024);
        System.out.println("[AudioClient] Using system default mic.");
        return line;
    }

    public void startCall(String serverIp, String username) {
        if (isCallActive) return;
        isCallActive = true;

        try {
            // Setup Microphone
            microphone = openBestMicLine();
            microphone.start();

            // Setup Speaker with small buffer
            DataLine.Info speakerInfo = new DataLine.Info(SourceDataLine.class, format);
            speaker = (SourceDataLine) AudioSystem.getLine(speakerInfo);
            speaker.open(format, 1024);
            speaker.start();

            // Thread to capture and send audio
            Thread captureThread = new Thread(() -> {
                byte[] buffer = new byte[512];
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
                byte[] buffer = new byte[512];
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

            // Send NAT hole-punch registration packet
            try {
                InetAddress serverAddress = InetAddress.getByName(serverIp);
                byte[] regData = ("REG:" + username).getBytes();
                DatagramPacket regPacket = new DatagramPacket(regData, regData.length, serverAddress, SERVER_PORT);
                udpSocket.send(regPacket);
            } catch (Exception e) {
                e.printStackTrace();
            }

            System.out.println("[AudioClient] Call started.");
        } catch (LineUnavailableException e) {
            System.err.println("[AudioClient] Audio line unavailable: " + e.getMessage());
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
        System.out.println("[AudioClient] Call stopped.");
    }
}
