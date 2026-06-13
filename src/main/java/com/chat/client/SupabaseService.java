package com.chat.client;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

public class SupabaseService {
    private static String supabaseUrl;
    private static String supabaseKey;
    private static boolean isConfigured = false;
    private static final HttpClient client = HttpClient.newHttpClient();
    private static final Gson gson = new Gson();

    static {
        loadConfig();
    }

    private static void loadConfig() {
        Properties props = new Properties();
        System.out.println("[Supabase] Working dir: " + System.getProperty("user.dir"));

        // Try multiple locations to find supabase.properties
        File configFile = findConfigFile();

        if (configFile != null && configFile.exists()) {
            try (FileInputStream in = new FileInputStream(configFile)) {
                props.load(in);
                supabaseUrl = props.getProperty("supabase.url");
                supabaseKey = props.getProperty("supabase.key");

                if (supabaseUrl != null && !supabaseUrl.isEmpty() && !supabaseUrl.contains("your-project-id") &&
                    supabaseKey != null && !supabaseKey.isEmpty() && !supabaseKey.contains("your-anon-or-service-role-key")) {
                    isConfigured = true;
                    // Strip trailing slashes
                    if (supabaseUrl.endsWith("/")) {
                        supabaseUrl = supabaseUrl.substring(0, supabaseUrl.length() - 1);
                    }
                    System.out.println("[Supabase] Configured successfully. URL: " + supabaseUrl + " (from: " + configFile.getAbsolutePath() + ")");
                } else {
                    System.err.println("[Supabase] Config found but contains placeholder values. Storage/DB will be disabled.");
                }
            } catch (IOException e) {
                System.err.println("[Supabase] Failed to read supabase.properties: " + e.getMessage());
            }
        } else {
            // Last resort: try loading from classpath resource
            try (java.io.InputStream is = SupabaseService.class.getResourceAsStream("/supabase.properties")) {
                if (is != null) {
                    props.load(is);
                    supabaseUrl = props.getProperty("supabase.url");
                    supabaseKey = props.getProperty("supabase.key");
                    if (supabaseUrl != null && !supabaseUrl.isEmpty() && !supabaseUrl.contains("your-project-id") &&
                        supabaseKey != null && !supabaseKey.isEmpty() && !supabaseKey.contains("your-anon-or-service-role-key")) {
                        isConfigured = true;
                        if (supabaseUrl.endsWith("/")) supabaseUrl = supabaseUrl.substring(0, supabaseUrl.length() - 1);
                        System.out.println("[Supabase] Configured from classpath resource. URL: " + supabaseUrl);
                    }
                } else {
                    System.err.println("[Supabase] No supabase.properties found in any location. Storage/DB will be disabled.");
                }
            } catch (IOException e) {
                System.err.println("[Supabase] Could not load classpath supabase.properties: " + e.getMessage());
            }
        }
    }

    /**
     * Searches several common locations for supabase.properties.
     */
    private static File findConfigFile() {
        // 1. Current working directory
        File f = new File("supabase.properties");
        if (f.exists()) return f;

        // 2. Location relative to the running JAR / class files
        try {
            java.security.CodeSource cs = SupabaseService.class.getProtectionDomain().getCodeSource();
            if (cs != null) {
                File jarDir = new File(cs.getLocation().toURI()).getParentFile();
                // Try jar dir and its parent (project root when running from target/)
                for (File dir : new File[]{jarDir, jarDir != null ? jarDir.getParentFile() : null}) {
                    if (dir != null) {
                        File candidate = new File(dir, "supabase.properties");
                        if (candidate.exists()) return candidate;
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }

        // 3. User home directory
        File homeCandidate = new File(System.getProperty("user.home"), "supabase.properties");
        if (homeCandidate.exists()) return homeCandidate;

        return null;
    }

    public static boolean isConfigured() {
        return isConfigured;
    }

    /**
     * Uploads a file to Supabase Storage bucket and returns the public URL.
     */
    public static String uploadFile(String bucket, String remotePath, File localFile) {
        if (!isConfigured) {
            System.err.println("Supabase Storage: Not configured. Cannot upload file.");
            return null;
        }

        try {
            // Determine MIME type first (before any copy, using the original filename)
            String name = localFile.getName().toLowerCase();
            String mimeType = Files.probeContentType(localFile.toPath());
            if (mimeType == null) {
                // Fallback mime-types based on file extension (Windows often returns null)
                if (name.endsWith(".png")) mimeType = "image/png";
                else if (name.endsWith(".jpg") || name.endsWith(".jpeg")) mimeType = "image/jpeg";
                else if (name.endsWith(".gif")) mimeType = "image/gif";
                else if (name.endsWith(".mp4")) mimeType = "video/mp4";
                else if (name.endsWith(".mkv")) mimeType = "video/x-matroska";
                else if (name.endsWith(".avi")) mimeType = "video/x-msvideo";
                else if (name.endsWith(".wav")) mimeType = "audio/wav";
                else mimeType = "application/octet-stream";
            }

            // --- OneDrive / Cloud Sync Fix ---
            // Files in OneDrive-synced folders (Desktop, Documents, Pictures) are stored as
            // cloud-only placeholders. Both Files.readAllBytes() AND Files.copy() use Java NIO
            // (FileChannel) which throws "The operation is reserved for a connected cloud sync
            // provider" on these files.
            //
            // ONLY Win32's CopyFile API triggers OneDrive to download the file first.
            // We invoke it via: cmd /c copy /Y "source" "dest"
            File readableFile = localFile;
            boolean isTempCopy = false;
            String tempDir = System.getProperty("java.io.tmpdir", "");
            if (!localFile.getAbsolutePath().startsWith(tempDir)) {
                String ext = name.contains(".") ? name.substring(name.lastIndexOf('.')) : "";
                readableFile = File.createTempFile("upload_", ext);
                readableFile.deleteOnExit();
                System.out.println("[Supabase] Copying file to temp dir (OneDrive-safe): " + localFile.getAbsolutePath() + " -> " + readableFile.getAbsolutePath());

                boolean copied = false;
                if (System.getProperty("os.name", "").toLowerCase().contains("windows")) {
                    // Use cmd.exe copy which calls Win32 CopyFile — properly triggers OneDrive download
                    try {
                        ProcessBuilder pb = new ProcessBuilder(
                            "cmd", "/c", "copy", "/Y",
                            localFile.getAbsolutePath(),
                            readableFile.getAbsolutePath()
                        );
                        pb.redirectErrorStream(true);
                        Process p = pb.start();
                        // Drain output to avoid process hanging
                        p.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
                        int exitCode = p.waitFor();
                        copied = (exitCode == 0 && readableFile.length() > 0);
                        System.out.println("[Supabase] cmd copy exit code: " + exitCode + ", size: " + readableFile.length());
                    } catch (Exception cmdEx) {
                        System.err.println("[Supabase] cmd copy failed: " + cmdEx.getMessage());
                    }
                }

                if (!copied) {
                    // Non-Windows or cmd copy failed: fall back to stream copy via URL (bypasses NIO)
                    try (java.io.InputStream in = localFile.toURI().toURL().openStream();
                         java.io.FileOutputStream out = new java.io.FileOutputStream(readableFile)) {
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                        copied = true;
                    } catch (Exception urlEx) {
                        System.err.println("[Supabase] URL stream copy failed: " + urlEx.getMessage());
                    }
                }

                if (copied) {
                    isTempCopy = true;
                } else {
                    // All copy strategies failed — try reading directly as last resort
                    readableFile = localFile;
                }
            }

            // --- Streaming upload (never loads the whole file into RAM) ---
            // Using ofFile() streams directly to Supabase — critical for large videos.
            // The temp copy (readableFile) must exist until the upload finishes.
            long fileSize = readableFile.length();
            System.out.println("[Supabase] Uploading " + fileSize + " bytes from: " + readableFile.getAbsolutePath());

            // Url Encode remotePath segment by segment to avoid breaking URLs
            String[] segments = remotePath.split("/");
            for (int i = 0; i < segments.length; i++) {
                segments[i] = URLEncoder.encode(segments[i], StandardCharsets.UTF_8).replace("+", "%20");
            }
            String encodedPath = String.join("/", segments);

            String uploadUrl = supabaseUrl + "/storage/v1/object/" + bucket + "/" + encodedPath;
            System.out.println("[Supabase] Uploading to: " + uploadUrl + " (" + fileSize + " bytes, " + mimeType + ")");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(uploadUrl))
                    .header("Authorization", "Bearer " + supabaseKey)
                    .header("apikey", supabaseKey)
                    .header("Content-Type", mimeType)
                    .header("x-upsert", "true")
                    // NOTE: Do NOT set Content-Length manually — Java HttpClient sets it
                    // automatically for ofFile() and throws IllegalArgumentException if you do.
                    .POST(HttpRequest.BodyPublishers.ofFile(readableFile.toPath()))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("[Supabase] Upload response [" + response.statusCode() + "]: " + response.body());

            // Clean up temp copy only after upload completes
            if (isTempCopy) {
                readableFile.delete();
            }

            if (response.statusCode() == 200 || response.statusCode() == 201) {
                String publicUrl = supabaseUrl + "/storage/v1/object/public/" + bucket + "/" + encodedPath;
                System.out.println("[Supabase] Upload successful. Public URL: " + publicUrl);
                return publicUrl;
            } else {
                System.err.println("[Supabase] Upload FAILED [" + response.statusCode() + "]: " + response.body());
                return null;
            }
        } catch (Exception e) {
            System.err.println("Exception uploading file to Supabase Storage: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Saves a message to the supabase 'messages' table.
     */
    public static boolean saveMessage(String sender, String target, String content, String mediaUrl, String mediaType) {
        if (!isConfigured) {
            return false;
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("sender", sender);
            payload.put("target", target);
            payload.put("content", content);
            payload.put("media_url", mediaUrl);
            payload.put("media_type", mediaType);

            String jsonPayload = gson.toJson(payload);
            String url = supabaseUrl + "/rest/v1/messages";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + supabaseKey)
                    .header("apikey", supabaseKey)
                    .header("Content-Type", applicationJsonHeaderValue())
                    .header("Prefer", "return=minimal")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception e) {
            System.err.println("Exception saving message to Supabase DB: " + e.getMessage());
            return false;
        }
    }

    /**
     * Retrieves messages for the active chat target (Global or Direct Message with another user).
     */
    public static List<Message> getMessages(String currentUsername, String target) {
        if (!isConfigured) {
            return Collections.emptyList();
        }

        try {
            String query;
            if ("Global".equals(target)) {
                query = "target=eq.Global";
            } else {
                // Query private DMs between currentUsername and target
                query = "or=(and(sender.eq." + URLEncoder.encode(currentUsername, StandardCharsets.UTF_8) +
                        ",target.eq." + URLEncoder.encode(target, StandardCharsets.UTF_8) + ")," +
                        "and(sender.eq." + URLEncoder.encode(target, StandardCharsets.UTF_8) +
                        ",target.eq." + URLEncoder.encode(currentUsername, StandardCharsets.UTF_8) + "))";
            }

            String url = supabaseUrl + "/rest/v1/messages?" + query + "&order=created_at.asc";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + supabaseKey)
                    .header("apikey", supabaseKey)
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return gson.fromJson(response.body(), new TypeToken<List<Message>>() {}.getType());
            } else {
                System.err.println("Failed to fetch messages from Supabase DB: " + response.body());
                return Collections.emptyList();
            }
        } catch (Exception e) {
            System.err.println("Exception fetching messages from Supabase DB: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    public static class AuthResult {
        public boolean success;
        public String errorMessage;
        public String username;
        public String accessToken;

        public AuthResult(boolean success, String errorMessage, String username, String accessToken) {
            this.success = success;
            this.errorMessage = errorMessage;
            this.username = username;
            this.accessToken = accessToken;
        }
    }

    /**
     * Registers a new user with email, password, and username in Supabase Auth.
     */
    public static AuthResult signUp(String email, String password, String username) {
        if (!isConfigured) {
            return new AuthResult(false, "Supabase is not configured.", null, null);
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("email", email);
            payload.put("password", password);

            Map<String, Object> options = new HashMap<>();
            Map<String, Object> data = new HashMap<>();
            data.put("username", username);
            options.put("data", data);
            payload.put("options", options);

            String jsonPayload = gson.toJson(payload);
            String url = supabaseUrl + "/auth/v1/signup";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + supabaseKey)
                    .header("apikey", supabaseKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200 || response.statusCode() == 201) {
                return new AuthResult(true, null, username, null);
            } else {
                String errorMsg = "Signup failed.";
                try {
                    Map<String, Object> respMap = gson.fromJson(response.body(), new TypeToken<Map<String, Object>>() {}.getType());
                    if (respMap != null) {
                        if (respMap.containsKey("msg")) {
                            errorMsg = (String) respMap.get("msg");
                        } else if (respMap.containsKey("error_description")) {
                            errorMsg = (String) respMap.get("error_description");
                        } else if (respMap.containsKey("error")) {
                            Object errorVal = respMap.get("error");
                            if (errorVal instanceof Map) {
                                errorMsg = (String) ((Map<?, ?>) errorVal).get("message");
                            } else {
                                errorMsg = String.valueOf(errorVal);
                            }
                        }
                    }
                } catch (Exception ex) {
                    errorMsg = "Error status " + response.statusCode();
                }
                return new AuthResult(false, errorMsg, null, null);
            }
        } catch (Exception e) {
            return new AuthResult(false, "Network error: " + e.getMessage(), null, null);
        }
    }

    /**
     * Signs in a user with email and password, retrieving their profile username.
     */
    public static AuthResult signIn(String email, String password) {
        if (!isConfigured) {
            return new AuthResult(false, "Supabase is not configured.", null, null);
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("email", email);
            payload.put("password", password);

            String jsonPayload = gson.toJson(payload);
            String url = supabaseUrl + "/auth/v1/token?grant_type=password";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + supabaseKey)
                    .header("apikey", supabaseKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200 || response.statusCode() == 201) {
                Map<String, Object> respMap = gson.fromJson(response.body(), new TypeToken<Map<String, Object>>() {}.getType());
                String accessToken = (String) respMap.get("access_token");
                
                String username = null;
                if (respMap.containsKey("user")) {
                    Map<?, ?> userMap = (Map<?, ?>) respMap.get("user");
                    if (userMap != null && userMap.containsKey("user_metadata")) {
                        Map<?, ?> metadataMap = (Map<?, ?>) userMap.get("user_metadata");
                        if (metadataMap != null) {
                            username = (String) metadataMap.get("username");
                        }
                    }
                }
                
                if (username == null || username.trim().isEmpty()) {
                    // Fallback to email prefix if username is not found in metadata
                    if (email.contains("@")) {
                        username = email.substring(0, email.indexOf("@"));
                    } else {
                        username = email;
                    }
                }
                
                return new AuthResult(true, null, username, accessToken);
            } else {
                String errorMsg = "Login failed.";
                try {
                    Map<String, Object> respMap = gson.fromJson(response.body(), new TypeToken<Map<String, Object>>() {}.getType());
                    if (respMap != null) {
                        if (respMap.containsKey("error_description")) {
                            errorMsg = (String) respMap.get("error_description");
                        } else if (respMap.containsKey("msg")) {
                            errorMsg = (String) respMap.get("msg");
                        } else if (respMap.containsKey("error")) {
                            errorMsg = String.valueOf(respMap.get("error"));
                        }
                    }
                } catch (Exception ex) {
                    errorMsg = "Error status " + response.statusCode();
                }
                return new AuthResult(false, errorMsg, null, null);
            }
        } catch (Exception e) {
            return new AuthResult(false, "Network error: " + e.getMessage(), null, null);
        }
    }

    /**
     * Retrieves unique usernames of users whom the current user has direct-messaged in the past.
     */
    public static List<String> getPastDMPartners(String currentUsername) {
        if (!isConfigured) {
            return Collections.emptyList();
        }

        try {
            String query = "or=(sender.eq." + URLEncoder.encode(currentUsername, StandardCharsets.UTF_8) +
                           ",target.eq." + URLEncoder.encode(currentUsername, StandardCharsets.UTF_8) + ")" +
                           "&target=neq.Global" +
                           "&select=sender,target";

            String url = supabaseUrl + "/rest/v1/messages?" + query;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + supabaseKey)
                    .header("apikey", supabaseKey)
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                List<Message> msgs = gson.fromJson(response.body(), new TypeToken<List<Message>>() {}.getType());
                Set<String> partners = new LinkedHashSet<>();
                for (Message m : msgs) {
                    if (m.sender != null && !m.sender.equals(currentUsername)) {
                        partners.add(m.sender);
                    }
                    if (m.target != null && !m.target.equals(currentUsername) && !m.target.equals("Global")) {
                        partners.add(m.target);
                    }
                }
                return new ArrayList<>(partners);
            } else {
                System.err.println("Failed to fetch DM partners from Supabase DB: " + response.body());
                return Collections.emptyList();
            }
        } catch (Exception e) {
            System.err.println("Exception fetching DM partners: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    private static String applicationJsonHeaderValue() {
        return "application/json";
    }

    public static class Message {
        public Long id;
        public String created_at;
        public String sender;
        public String target;
        public String content;
        public String media_url;
        public String media_type;

        @Override
        public String toString() {
            return "Message{" +
                    "sender='" + sender + '\'' +
                    ", content='" + content + '\'' +
                    ", mediaUrl='" + media_url + '\'' +
                    ", mediaType='" + media_type + '\'' +
                    '}';
        }
    }
}
