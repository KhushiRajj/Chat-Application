package com.chat.client;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class VideoCompressor {

    private static String cachedFFmpegPath = null;

    /**
     * Checks if FFmpeg is available on the system.
     */
    public static boolean isFFmpegAvailable() {
        if (cachedFFmpegPath != null) return true;

        // Try direct call
        try {
            Process process = new ProcessBuilder("ffmpeg", "-version").start();
            process.waitFor();
            cachedFFmpegPath = "ffmpeg";
            return true;
        } catch (Exception e) {
            // Try discovered WinGet PATH
            try {
                String localAppData = System.getenv("LOCALAPPDATA");
                if (localAppData != null) {
                    String winGetPath = localAppData + "\\Microsoft\\WinGet\\Links\\ffmpeg.exe";
                    File f = new File(winGetPath);
                    if (f.exists()) {
                        Process process = new ProcessBuilder(winGetPath, "-version").start();
                        process.waitFor();
                        cachedFFmpegPath = winGetPath;
                        return true;
                    }
                }
            } catch (Exception ex) {
                // Ignore
            }
        }
        return false;
    }

    /**
     * Compresses the input video file using FFmpeg.
     * Returns the compressed File, or the original file if compression fails or FFmpeg is unavailable.
     */
    public static File compressVideo(File inputVideo) {
        if (!isFFmpegAvailable()) {
            System.out.println("FFmpeg not available. Returning original video file: " + inputVideo.length() + " bytes");
            return inputVideo;
        }

        try {
            File tempOutput = File.createTempFile("compressed_", ".mp4");
            tempOutput.deleteOnExit(); // Clean up on exit

            List<String> command = new ArrayList<>();
            command.add(cachedFFmpegPath);
            command.add("-y"); // Overwrite output files without asking
            command.add("-i");
            command.add(inputVideo.getAbsolutePath());
            command.add("-vcodec");
            command.add("libx264");
            command.add("-crf");
            command.add("28"); // CRF 28 is a good balance between compression and quality
            command.add("-preset");
            command.add("fast");
            command.add("-acodec");
            command.add("aac");
            command.add("-b:a");
            command.add("128k");
            command.add(tempOutput.getAbsolutePath());

            System.out.println("Compressing video from: " + inputVideo.getAbsolutePath() + " to: " + tempOutput.getAbsolutePath());

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Read the process output to prevent it from locking up
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                // Verbose logging of progress if needed (could log to console for debugging)
            }

            int exitCode = process.waitFor();
            if (exitCode == 0 && tempOutput.exists() && tempOutput.length() > 0) {
                System.out.println("Video compressed successfully! New size: " + tempOutput.length() + " bytes (Original: " + inputVideo.length() + " bytes)");
                return tempOutput;
            } else {
                System.err.println("FFmpeg execution finished with error code: " + exitCode + ". Returning original file.");
                return inputVideo;
            }
        } catch (IOException | InterruptedException e) {
            System.err.println("Failed to compress video, returning original: " + e.getMessage());
            e.printStackTrace();
            return inputVideo;
        }
    }
}
