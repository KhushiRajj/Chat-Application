# Chat Application (Real-Time Java & JavaFX Chat)

A modern, feature-rich, multi-user real-time chat application built with **JavaFX** for the client and a custom **Java TCP & UDP Socket Server** for signaling and audio streaming. Media assets (Images, Videos, and Voice Notes) are stored securely and served via **Supabase Storage**.

---

## Key Features

*   **Real-time Text Messaging**: Sub-second message delivery between clients via TCP sockets.
*   **Private Chatting**: Direct messages to specific users using the `/msg <username> <message>` format.
*   **Media Sharing**: Share images, video attachments, and voice notes.
*   **Dynamic Audio Selection**: Auto-detects connected audio inputs (e.g., Bluetooth headsets like *OnePlus BulletsWireless Z2 ANC*) and allows manual or automatic hot-plug refreshes.
*   **Zero-RAM Streaming Upload**: Large files (e.g., videos) are streamed using Java's HTTP Client without consuming JVM heap memory, preventing Out-Of-Memory (OOM) crashes.
*   **Video Compression**: Automatically compresses large videos locally before uploading to optimize bandwidth.
*   **Multi-Theme UI**: Gorgeous user interface with Dark, Light, and Ocean themes.

---

## Prerequisites

Ensure you have the following installed on your system:
*   **Java JDK 17** or higher (e.g., OpenJDK 17).
*   **Apache Maven 3.6** or higher.
*   An active **Supabase** project with a storage bucket configured.

---

## Configuration

The application requires connection details to your **Supabase** instance to upload images, videos, and voice notes.

1. Locate or create a file named `supabase.properties` in the project root folder (or inside `src/main/resources/`).
2. Add your Supabase project URL and anonymous key:

```properties
# Supabase Configuration
supabase.url=https://your-project-id.supabase.co
supabase.key=your-anon-or-service-role-api-key
```

### Supabase Storage Bucket Setup
To allow media uploads, make sure to set up a bucket in your Supabase Storage console:
*   **Bucket Name**: `chat-media`
*   **Public Access**: Enabled (Public bucket)
*   **MIME Restrictions**: `image/*`, `video/*`, `audio/*`
*   **Row Level Security (RLS) Policies**: Ensure the `anon` role has permission to **INSERT** (and optionally **SELECT**) objects in the `chat-media` bucket.

> [!TIP]
> You can create this bucket programmatically by running the scratch script `create_bucket.py` located in your environment if Python is installed:
> ```bash
> python create_bucket.py
> ```

---

## Building and Compiling

To download dependencies and compile all classes, run the standard Maven clean and compile phases:

```bash
mvn clean compile
```

This compiles both the server and client files, placing the built classes under `target/classes/`.

---

## Running the Application

For a fully working chat system, you must start the **Server** first, followed by one or more **Clients**.

### 1. Start the Chat Server
The server manages TCP connection handling, message routing, and UDP voice routing. 

Run the server with the following command:
```bash
mvn exec:java@server
```

*   **TCP Port**: `5000` (Used for user authentication, text messaging, and user lists)
*   **UDP Port**: `5001` (Used for routing voice notes and real-time audio streams)

---

### 2. Start the Chat Client
You can start one or multiple clients on the same machine or across a local network:

```bash
mvn javafx:run
```

#### Client Connection Setup
*   When the UI opens, enter your preferred **Username**.
*   Enter the **Server IP Address** (use `localhost` if running the server on the same machine).
*   Select your preferred **Microphone / Input Source** from the top bar dropdown menu.

> [!NOTE]
> If you connect or disconnect a microphone (or headset) while the client is running, the client will automatically detect the device change within 3 seconds. You can also manually reload the input list by clicking the refresh icon (**🔄**) next to the microphone dropdown.

---

## Packaging into a Standalone Executable (Fat JAR)

To bundle the application, JavaFX modules, and all library dependencies (like Gson) into a single executable Fat JAR, run:

```bash
mvn clean package
```

The build process will produce a shaded JAR at:
`target/ChatApplication-1.0-SNAPSHOT.jar`

You can run this JAR on any system with Java installed:
```bash
java -jar target/ChatApplication-1.0-SNAPSHOT.jar
```

---

## Troubleshooting

### File / Voice Upload Fails
If you receive a "File Upload Failed" dialog, ensure that:
1. The Supabase storage bucket `chat-media` exists and is public.
2. The URL and key in your `supabase.properties` file are correct and do not contain trailing spaces or placeholder values.
3. The folder you are uploading from is accessible (the application features an automated workaround to fetch cloud-only OneDrive files on Windows).

### Audio Input Selection Issues
*   Ensure the selected microphone is not being used exclusively by another program.
*   If your Bluetooth headset (e.g. *OnePlus Bullets Wireless*) does not capture sound, click the **🔄** button to refresh the list, verify it is selected in the dropdown list, and ensure it is set as your default communication device in Windows Sound settings.
