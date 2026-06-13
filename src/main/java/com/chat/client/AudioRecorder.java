package com.chat.client;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AudioRecorder {
    // Standard audio format: 22050 Hz, 16 bit, Mono, Signed, Little-Endian
    private static final AudioFormat FORMAT = new AudioFormat(22050.0f, 16, 1, true, false);

    private TargetDataLine targetLine;
    private boolean isRecording = false;
    private ByteArrayOutputStream recordStream;
    private Thread recordThread;

    // Tracks current amplitude (volume level) for real-time visualization
    private double currentAmplitude = 0.0;

    // Currently selected microphone name (null = use system default)
    private String selectedMicrophoneName = null;

    /**
     * Set the microphone device to use for recording (by mixer name).
     * Pass null to use the system default.
     */
    public void setMicrophone(String mixerName) {
        this.selectedMicrophoneName = mixerName;
    }

    /**
     * Returns the currently selected microphone name, or null for default.
     */
    public String getSelectedMicrophone() {
        return selectedMicrophoneName;
    }

    /**
     * Returns a live list of all available input (microphone) devices.
     * This is called fresh each time so newly connected devices are included.
     */
    public static List<String> getAvailableMicrophones() {
        List<String> mics = new ArrayList<>();
        DataLine.Info targetInfo = new DataLine.Info(TargetDataLine.class, FORMAT);
        for (Mixer.Info mixerInfo : AudioSystem.getMixerInfo()) {
            try {
                Mixer mixer = AudioSystem.getMixer(mixerInfo);
                // Check if this mixer supports our target data line format
                if (mixer.isLineSupported(targetInfo)) {
                    mics.add(mixerInfo.getName());
                }
            } catch (Exception ignored) {}
        }
        return mics;
    }

    /**
     * Opens the best available TargetDataLine for recording.
     * Tries selectedMicrophoneName first, falls back to system default.
     */
    private TargetDataLine openBestLine() throws LineUnavailableException {
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);

        // 1. Try the user-selected device
        if (selectedMicrophoneName != null) {
            for (Mixer.Info mixerInfo : AudioSystem.getMixerInfo()) {
                if (mixerInfo.getName().equals(selectedMicrophoneName)) {
                    try {
                        Mixer mixer = AudioSystem.getMixer(mixerInfo);
                        TargetDataLine line = (TargetDataLine) mixer.getLine(info);
                        line.open(FORMAT);
                        System.out.println("[AudioRecorder] Using selected mic: " + mixerInfo.getName());
                        return line;
                    } catch (Exception e) {
                        System.err.println("[AudioRecorder] Selected mic unavailable (" + mixerInfo.getName() + "): " + e.getMessage() + " — falling back.");
                    }
                }
            }
        }

        // 2. Fall back: try each available mixer and use the first that works
        for (Mixer.Info mixerInfo : AudioSystem.getMixerInfo()) {
            try {
                Mixer mixer = AudioSystem.getMixer(mixerInfo);
                if (!mixer.isLineSupported(info)) continue;
                TargetDataLine line = (TargetDataLine) mixer.getLine(info);
                line.open(FORMAT);
                System.out.println("[AudioRecorder] Auto-selected mic: " + mixerInfo.getName());
                return line;
            } catch (Exception ignored) {}
        }

        // 3. Last resort: system default
        if (!AudioSystem.isLineSupported(info)) {
            throw new LineUnavailableException("No compatible audio input device found.");
        }
        TargetDataLine line = (TargetDataLine) AudioSystem.getLine(info);
        line.open(FORMAT);
        System.out.println("[AudioRecorder] Using system default mic.");
        return line;
    }

    /**
     * Start recording audio from the selected (or best available) microphone.
     */
    public void startRecording() throws LineUnavailableException {
        if (isRecording) return;

        targetLine = openBestLine();
        targetLine.start();

        isRecording = true;
        recordStream = new ByteArrayOutputStream();
        currentAmplitude = 0.0;

        recordThread = new Thread(() -> {
            byte[] buffer = new byte[1024];
            while (isRecording) {
                int bytesRead = targetLine.read(buffer, 0, buffer.length);
                if (bytesRead > 0) {
                    recordStream.write(buffer, 0, bytesRead);
                    calculateAmplitude(buffer, bytesRead);
                }
            }
        });

        recordThread.setDaemon(true);
        recordThread.start();
        System.out.println("[AudioRecorder] Recording started.");
    }

    /**
     * Calculate average amplitude (RMS) from the 16-bit PCM buffer to feed UI visualizers.
     */
    private void calculateAmplitude(byte[] buffer, int bytesRead) {
        long sum = 0;
        int count = bytesRead / 2; // 16-bit is 2 bytes per sample
        if (count == 0) return;

        for (int i = 0; i < bytesRead - 1; i += 2) {
            // Little-endian reconstruction of 16-bit sample
            short sample = (short) ((buffer[i] & 0xFF) | (buffer[i + 1] << 8));
            sum += (long) sample * sample;
        }

        double rms = Math.sqrt((double) sum / count);
        // Normalize to a 0.0 to 1.0 range based on 16-bit max amplitude (32767)
        currentAmplitude = Math.min(1.0, rms / 32768.0);
    }

    /**
     * Get the current voice amplitude level (between 0.0 and 1.0).
     */
    public double getCurrentAmplitude() {
        if (!isRecording) return 0.0;
        return currentAmplitude;
    }

    public boolean isRecording() {
        return isRecording;
    }

    /**
     * Stop recording and write the buffer out to a temporary WAV file.
     * Returns the recorded WAV File, or null if nothing was recorded.
     */
    public File stopRecording() {
        if (!isRecording) return null;

        isRecording = false;
        currentAmplitude = 0.0;

        if (targetLine != null) {
            targetLine.stop();
            targetLine.close();
        }

        try {
            if (recordThread != null) {
                recordThread.join(1000); // Wait for thread to finish writing
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        byte[] audioBytes = recordStream.toByteArray();
        if (audioBytes.length == 0) {
            return null;
        }

        try {
            File tempWav = File.createTempFile("voice_note_", ".wav");
            tempWav.deleteOnExit();

            ByteArrayInputStream bais = new ByteArrayInputStream(audioBytes);
            AudioInputStream ais = new AudioInputStream(
                bais,
                FORMAT,
                audioBytes.length / FORMAT.getFrameSize()
            );

            // Write as standard WAVE file
            AudioSystem.write(ais, AudioFileFormat.Type.WAVE, tempWav);
            System.out.println("[AudioRecorder] Voice note saved: " + tempWav.getAbsolutePath() + " (" + tempWav.length() + " bytes)");
            return tempWav;
        } catch (IOException e) {
            System.err.println("[AudioRecorder] Failed to write WAV file: " + e.getMessage());
            return null;
        }
    }
}
