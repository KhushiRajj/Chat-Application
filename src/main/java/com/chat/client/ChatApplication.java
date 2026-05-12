package com.chat.client;

import javafx.animation.FadeTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatApplication extends Application {

    private ChatClient chatClient;
    private AudioClient audioClient;
    private String currentUsername;
    private String serverIp;

    private Stage primaryStage;
    
    // UI Components for Main App
    private BorderPane mainRoot;
    private VBox messageContainer;
    private ScrollPane chatScrollPane;
    private TextField messageField;
    private ListView<String> userList;
    private ListView<String> dmList;
    private Label topBarTitle;
    private Button voiceCallBtn;
    
    // State
    private String currentActiveChat = "Global"; // "Global" or username
    private Map<String, List<HBox>> chatHistories = new HashMap<>();
    
    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        chatClient = new ChatClient();
        audioClient = new AudioClient();
        
        chatHistories.put("Global", new ArrayList<>());

        showLoginScreen();
    }

    private void showLoginScreen() {
        VBox root = new VBox();
        root.setAlignment(Pos.CENTER);
        root.getStyleClass().add("login-bg");

        VBox card = new VBox(15);
        card.setMaxWidth(350);
        card.getStyleClass().add("login-card");
        card.setAlignment(Pos.CENTER);

        Label title = new Label("Welcome Back!");
        title.setStyle("-fx-font-size: 24px; -fx-text-fill: white; -fx-font-weight: bold;");
        Label subtitle = new Label("We're so excited to see you again!");
        subtitle.setStyle("-fx-text-fill: #b9bbbe; -fx-font-size: 14px; -fx-padding: 0 0 10 0;");

        VBox fieldsBox = new VBox(10);
        
        Label ipLabel = new Label("SERVER IP");
        ipLabel.getStyleClass().add("header-label");
        ipLabel.setPadding(new Insets(0));
        TextField serverIpField = new TextField("localhost");
        serverIpField.getStyleClass().add("chat-text-field");

        Label userLabel = new Label("USERNAME");
        userLabel.getStyleClass().add("header-label");
        userLabel.setPadding(new Insets(10, 0, 0, 0));
        TextField usernameField = new TextField();
        usernameField.getStyleClass().add("chat-text-field");

        Button loginBtn = new Button("Login");
        loginBtn.getStyleClass().add("btn-primary");
        loginBtn.setMaxWidth(Double.MAX_VALUE);
        loginBtn.setPadding(new Insets(10));

        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: #ed4245;");

        fieldsBox.getChildren().addAll(ipLabel, serverIpField, userLabel, usernameField);

        loginBtn.setOnAction(e -> {
            String ip = serverIpField.getText().trim();
            if (ip.isEmpty()) ip = "localhost";
            String username = usernameField.getText().trim();
            
            if (username.isEmpty()) {
                errorLabel.setText("Username cannot be empty");
                return;
            }

            // Connect
            int audioPort = audioClient.getLocalPort();
            boolean success = chatClient.connect(ip, username, audioPort);
            
            if (success) {
                currentUsername = username;
                serverIp = ip;
                
                // Now that UI is ready, set callbacks and start listening
                setupCallbacks();
                chatClient.startListening();
                
                showMainChatScreen();
            } else {
                errorLabel.setText("Connection failed or username taken.");
            }
        });
        
        usernameField.setOnAction(loginBtn.getOnAction());

        card.getChildren().addAll(title, subtitle, fieldsBox, loginBtn, errorLabel);
        root.getChildren().add(card);
        
        // Sleek entry animation
        card.setOpacity(0);
        card.setTranslateY(30);

        FadeTransition fade = new FadeTransition(Duration.millis(800), card);
        fade.setFromValue(0);
        fade.setToValue(1);

        TranslateTransition translate = new TranslateTransition(Duration.millis(800), card);
        translate.setFromY(30);
        translate.setToY(0);

        fade.play();
        translate.play();
        
        Scene scene = new Scene(root, 800, 600);
        try {
            scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
        } catch (Exception e) {
            System.err.println("Could not load style.css");
        }
        
        primaryStage.setTitle("Login");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void setupCallbacks() {
        chatClient.setOnMessageReceived(message -> Platform.runLater(() -> handleIncomingMessage(message)));
        chatClient.setOnUserListUpdated(users -> Platform.runLater(() -> updateUsersList(users)));
    }

    private void showMainChatScreen() {
        mainRoot = new BorderPane();
        mainRoot.getStyleClass().add("root");

        // --- LEFT SIDEBAR (Channels & DMs) ---
        VBox leftSidebar = new VBox();
        leftSidebar.getStyleClass().add("sidebar");
        leftSidebar.setPrefWidth(240);

        Label channelsHeader = new Label("TEXT CHANNELS");
        channelsHeader.getStyleClass().add("header-label");
        
        ListView<String> channelList = new ListView<>();
        channelList.getStyleClass().add("list-view");
        channelList.getItems().add("# global-chat");
        channelList.setPrefHeight(60);
        channelList.getSelectionModel().selectFirst();
        
        Label dmHeader = new Label("DIRECT MESSAGES");
        dmHeader.getStyleClass().add("header-label");
        
        dmList = new ListView<>();
        dmList.getStyleClass().add("list-view");
        VBox.setVgrow(dmList, Priority.ALWAYS);
        
        channelList.setOnMouseClicked(e -> {
            if (channelList.getSelectionModel().getSelectedItem() != null) {
                dmList.getSelectionModel().clearSelection();
                switchChat("Global");
            }
        });
        
        dmList.setOnMouseClicked(e -> {
            String selected = dmList.getSelectionModel().getSelectedItem();
            if (selected != null) {
                channelList.getSelectionModel().clearSelection();
                switchChat(selected);
            }
        });

        leftSidebar.getChildren().addAll(channelsHeader, channelList, dmHeader, dmList);
        mainRoot.setLeft(leftSidebar);

        // --- CENTER AREA (Chat) ---
        BorderPane centerArea = new BorderPane();

        // Top bar
        HBox topBar = new HBox();
        topBar.getStyleClass().add("top-bar");
        topBar.setPadding(new Insets(15));
        topBar.setAlignment(Pos.CENTER_LEFT);
        
        topBarTitle = new Label("# global-chat");
        topBarTitle.getStyleClass().add("title-label");
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        voiceCallBtn = new Button("Join Voice Channel");
        voiceCallBtn.getStyleClass().add("btn-primary");
        voiceCallBtn.setOnAction(e -> toggleVoiceCall());
        
        topBar.getChildren().addAll(topBarTitle, spacer, voiceCallBtn);
        centerArea.setTop(topBar);

        // Messages Area
        chatScrollPane = new ScrollPane();
        chatScrollPane.getStyleClass().add("chat-scroll-pane");
        chatScrollPane.setFitToWidth(true);
        chatScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        chatScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        
        messageContainer = new VBox(0);
        messageContainer.setPadding(new Insets(10, 0, 10, 0));
        chatScrollPane.setContent(messageContainer);
        
        // Auto scroll
        messageContainer.heightProperty().addListener((obs, oldVal, newVal) -> {
            chatScrollPane.setVvalue(1.0);
        });

        // Input Area
        HBox inputArea = new HBox();
        inputArea.getStyleClass().add("input-area");
        
        messageField = new TextField();
        messageField.getStyleClass().add("chat-text-field");
        messageField.setPromptText("Message #global-chat");
        HBox.setHgrow(messageField, Priority.ALWAYS);
        
        messageField.setOnAction(e -> sendMessage());
        
        inputArea.getChildren().add(messageField);
        
        centerArea.setCenter(chatScrollPane);
        centerArea.setBottom(inputArea);
        
        mainRoot.setCenter(centerArea);

        // --- RIGHT SIDEBAR (Online Users) ---
        VBox rightSidebar = new VBox();
        rightSidebar.getStyleClass().add("user-list-sidebar");
        rightSidebar.setPrefWidth(240);
        
        Label usersLabel = new Label("ONLINE");
        usersLabel.getStyleClass().add("header-label");
        
        userList = new ListView<>();
        userList.getStyleClass().add("list-view");
        VBox.setVgrow(userList, Priority.ALWAYS);
        
        userList.setCellFactory(lv -> new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    HBox box = new HBox(10);
                    box.setAlignment(Pos.CENTER_LEFT);
                    
                    Circle statusDot = new Circle(5);
                    statusDot.setStyle("-fx-fill: #43b581;"); // Discord Online Green
                    
                    Label nameLabel = new Label(item);
                    nameLabel.setStyle("-fx-text-fill: #EFEFEF; -fx-font-size: 14px;");
                    
                    box.getChildren().addAll(statusDot, nameLabel);
                    setGraphic(box);
                }
            }
        });
        
        // Double click to DM
        userList.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                String selectedUser = userList.getSelectionModel().getSelectedItem();
                if (selectedUser != null && !selectedUser.equals(currentUsername)) {
                    openDirectMessage(selectedUser);
                }
            }
        });
        
        rightSidebar.getChildren().addAll(usersLabel, userList);
        mainRoot.setRight(rightSidebar);

        // Load scene
        Scene scene = new Scene(mainRoot, 1000, 700);
        try {
            scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
        } catch (Exception e) {
            System.err.println("Could not load style.css");
        }
        
        primaryStage.setTitle("Discord-like Chat Application");
        primaryStage.setScene(scene);
        
        primaryStage.setOnCloseRequest(e -> {
            chatClient.close();
            audioClient.stopCall();
            System.exit(0);
        });
        
        // Initially render global chat
        renderChat("Global");
    }

    private void switchChat(String chatName) {
        currentActiveChat = chatName;
        if (chatName.equals("Global")) {
            topBarTitle.setText("# global-chat");
            messageField.setPromptText("Message #global-chat");
        } else {
            topBarTitle.setText("@ " + chatName);
            messageField.setPromptText("Message @" + chatName);
        }
        renderChat(chatName);
    }

    private void openDirectMessage(String targetUser) {
        if (!chatHistories.containsKey(targetUser)) {
            chatHistories.put(targetUser, new ArrayList<>());
            dmList.getItems().add(targetUser);
        }
        dmList.getSelectionModel().select(targetUser);
        switchChat(targetUser);
    }

    private void renderChat(String chatName) {
        messageContainer.getChildren().clear();
        List<HBox> messages = chatHistories.getOrDefault(chatName, new ArrayList<>());
        messageContainer.getChildren().addAll(messages);
    }

    private String getUsernameColor(String username) {
        if (username.equals(currentUsername)) return "#43b581"; // Self is green
        if (username.equals("[SERVER]")) return "#faa61a"; // Server is yellow
        
        String[] colors = {
            "#0084FF", "#f04747", "#593695", "#f47fff", 
            "#1abc9c", "#e67e22", "#3498db", "#9b59b6"
        };
        int hash = Math.abs(username.hashCode());
        return colors[hash % colors.length];
    }

    private HBox createMessageBubble(String sender, String text) {
        VBox bubble = new VBox(2);
        
        Label senderLabel = new Label(sender);
        senderLabel.getStyleClass().add("message-sender");
        senderLabel.setStyle("-fx-text-fill: " + getUsernameColor(sender) + ";");
        
        Label textLabel = new Label(text);
        textLabel.getStyleClass().add("message-text");
        
        bubble.getChildren().addAll(senderLabel, textLabel);
        
        HBox container = new HBox();
        container.setPadding(new Insets(5, 20, 5, 20));
        
        if (sender.equals(currentUsername)) {
            bubble.getStyleClass().add("message-bubble-self");
            container.setAlignment(Pos.CENTER_RIGHT);
        } else {
            bubble.getStyleClass().add("message-bubble-other");
            container.setAlignment(Pos.CENTER_LEFT);
        }
        
        container.getChildren().add(bubble);
        return container;
    }

    private void appendToHistory(String chatName, String sender, String text) {
        chatHistories.putIfAbsent(chatName, new ArrayList<>());
        HBox bubble = createMessageBubble(sender, text);
        chatHistories.get(chatName).add(bubble);
        
        if (currentActiveChat.equals(chatName)) {
            messageContainer.getChildren().add(bubble);
        }
    }

    private void sendMessage() {
        String msg = messageField.getText().trim();
        if (!msg.isEmpty()) {
            if (currentActiveChat.equals("Global")) {
                chatClient.sendMessage(msg);
                appendToHistory("Global", currentUsername, msg);
            } else {
                chatClient.sendPrivateMessage(currentActiveChat, msg);
                appendToHistory(currentActiveChat, currentUsername, msg);
            }
            messageField.clear();
        }
    }

    private void handleIncomingMessage(String message) {
        // Formats:
        // Global: "User: message"
        // Private: "[Private from User]: msg"
        // Server: "[SERVER] message" or "[Server]: msg"
        
        if (message.startsWith("[Private from ")) {
            try {
                int endBracket = message.indexOf("]");
                // Fixed the off-by-one substring error:
                String sender = message.substring(14, endBracket);
                String actualMsg = message.substring(endBracket + 3);
                
                // Ensure DM exists in list
                if (!chatHistories.containsKey(sender)) {
                    chatHistories.put(sender, new ArrayList<>());
                    dmList.getItems().add(sender);
                }
                appendToHistory(sender, sender, actualMsg);
            } catch (Exception e) {
                appendToHistory("Global", "[SERVER]", message);
            }
        } else if (message.startsWith("[SERVER]") || message.startsWith("[Server]")) {
            appendToHistory("Global", "[SERVER]", message.substring(8).trim());
        } else {
            int colonIdx = message.indexOf(":");
            if (colonIdx != -1) {
                String sender = message.substring(0, colonIdx);
                String actualMsg = message.substring(colonIdx + 1).trim();
                appendToHistory("Global", sender, actualMsg);
            } else {
                appendToHistory("Global", "[SERVER]", message);
            }
        }
    }

    private void updateUsersList(String[] users) {
        userList.getItems().clear();
        for (String u : users) {
            if (!u.trim().isEmpty()) {
                userList.getItems().add(u.trim());
            }
        }
    }

    private void toggleVoiceCall() {
        if (voiceCallBtn.getText().equals("Join Voice Channel")) {
            audioClient.startCall(serverIp);
            voiceCallBtn.setText("Disconnect");
            voiceCallBtn.getStyleClass().remove("btn-primary");
            voiceCallBtn.getStyleClass().add("btn-danger");
        } else {
            audioClient.stopCall();
            voiceCallBtn.setText("Join Voice Channel");
            voiceCallBtn.getStyleClass().remove("btn-danger");
            voiceCallBtn.getStyleClass().add("btn-primary");
        }
    }
}
