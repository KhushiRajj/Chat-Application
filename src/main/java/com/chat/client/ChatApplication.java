package com.chat.client;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javafx.animation.AnimationTimer;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

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
    
    // Media, Recording & Async Executor
    private final AudioRecorder audioRecorder = new AudioRecorder();
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private Timeline recordingTimeline;
    private int recordingSeconds = 0;
    private Button recordBtn;
    private HBox visualizerContainer;
    private Label recordingTimerLabel;
    private Button attachBtn;
    private Button emojiBtn;
    private HBox recordingInputPlaceholder;
    private javafx.scene.shape.Circle recordingDot;
    private FadeTransition recordPulseTransition;
    private String currentActiveChat = "Global"; // "Global" or username
    private Map<String, List<HBox>> chatHistories = new HashMap<>();
    
    // Customization Settings
    private String currentTheme = "dark-theme.css";
    private String currentWallpaper = "https://images.pexels.com/photos/1103970/pexels-photo-1103970.jpeg?auto=compress&cs=tinysrgb&w=1260&h=750&dpr=1"; // Pexels Abstract Dark
    private String currentAvatarUrl = "https://cdn-icons-png.flaticon.com/512/4140/4140048.png";
    
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

    private boolean isRegistering = false; // Toggle state for Auth Mode

    private void showLoginScreen() {
        VBox root = new VBox();
        root.setAlignment(Pos.CENTER);
        root.getStyleClass().add("login-bg");

        VBox card = new VBox(15);
        card.setMaxWidth(380);
        card.getStyleClass().add("login-card");
        card.setAlignment(Pos.CENTER);

        // Header Labels
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

        Label emailLabel = new Label("EMAIL");
        emailLabel.getStyleClass().add("header-label");
        emailLabel.setPadding(new Insets(10, 0, 0, 0));
        TextField emailField = new TextField();
        emailField.getStyleClass().add("chat-text-field");
        emailField.setPromptText("name@example.com");

        Label passwordLabel = new Label("PASSWORD");
        passwordLabel.getStyleClass().add("header-label");
        passwordLabel.setPadding(new Insets(10, 0, 0, 0));
        PasswordField passwordField = new PasswordField();
        passwordField.getStyleClass().add("chat-text-field");

        Label userLabel = new Label("USERNAME");
        userLabel.getStyleClass().add("header-label");
        userLabel.setPadding(new Insets(10, 0, 0, 0));
        TextField usernameField = new TextField();
        usernameField.getStyleClass().add("chat-text-field");
        usernameField.setPromptText("Display name");

        // Primary Button
        Button actionBtn = new Button("Login");
        actionBtn.getStyleClass().add("btn-primary");
        actionBtn.setMaxWidth(Double.MAX_VALUE);
        actionBtn.setPadding(new Insets(10));

        // Secondary toggle option
        Hyperlink toggleLink = new Hyperlink("Need an account? Register");
        toggleLink.setStyle("-fx-text-fill: #0084FF; -fx-font-size: 13px; -fx-underline: false;");

        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: #ed4245; -fx-wrap-text: true; -fx-text-alignment: center;");

        // Initial setup for LOGIN mode (username field not initially in fieldsBox)
        fieldsBox.getChildren().addAll(ipLabel, serverIpField, emailLabel, emailField, passwordLabel, passwordField);

        // Helper to update views based on mode
        Runnable updateAuthMode = () -> {
            fieldsBox.getChildren().clear();
            errorLabel.setText("");
            if (isRegistering) {
                title.setText("Create an account");
                subtitle.setText("Enter your details below to get started!");
                actionBtn.setText("Sign Up");
                toggleLink.setText("Already have an account? Login");
                fieldsBox.getChildren().addAll(ipLabel, serverIpField, emailLabel, emailField, passwordLabel, passwordField, userLabel, usernameField);
            } else {
                title.setText("Welcome Back!");
                subtitle.setText("We're so excited to see you again!");
                actionBtn.setText("Login");
                toggleLink.setText("Need an account? Register");
                fieldsBox.getChildren().addAll(ipLabel, serverIpField, emailLabel, emailField, passwordLabel, passwordField);
            }
        };

        toggleLink.setOnAction(e -> {
            isRegistering = !isRegistering;
            updateAuthMode.run();
        });

        actionBtn.setOnAction(e -> {
            String ip = serverIpField.getText().trim();
            if (ip.isEmpty()) ip = "localhost";
            String email = emailField.getText().trim();
            String password = passwordField.getText();
            String usernameVal = usernameField.getText().trim();
            
            if (email.isEmpty()) {
                errorLabel.setText("Email cannot be empty");
                return;
            }
            if (password.isEmpty()) {
                errorLabel.setText("Password cannot be empty");
                return;
            }

            if (isRegistering && usernameVal.isEmpty()) {
                errorLabel.setText("Username cannot be empty");
                return;
            }

            final String finalIp = ip;

            actionBtn.setDisable(true);
            toggleLink.setDisable(true);
            errorLabel.setText("Authenticating...");

            executorService.submit(() -> {
                if (isRegistering) {
                    // Sign up first
                    SupabaseService.AuthResult signUpRes = SupabaseService.signUp(email, password, usernameVal);
                    if (!signUpRes.success) {
                        Platform.runLater(() -> {
                            actionBtn.setDisable(false);
                            toggleLink.setDisable(false);
                            errorLabel.setText("Sign Up failed: " + signUpRes.errorMessage);
                        });
                        return;
                    }
                    // Inform user and let them login
                    Platform.runLater(() -> {
                        actionBtn.setDisable(false);
                        toggleLink.setDisable(false);
                        errorLabel.setText("Signup successful! Logging you in...");
                    });
                }

                // Sign in
                SupabaseService.AuthResult loginRes = SupabaseService.signIn(email, password);
                if (loginRes.success) {
                    String username = loginRes.username;
                    // Connect to TCP server
                    int audioPort = audioClient.getLocalPort();
                    boolean tcpSuccess = chatClient.connect(finalIp, username, audioPort);
                    
                    Platform.runLater(() -> {
                        if (tcpSuccess) {
                            currentUsername = username;
                            serverIp = finalIp;
                            
                            setupCallbacks();
                            chatClient.startListening();
                            showMainChatScreen();
                        } else {
                            actionBtn.setDisable(false);
                            toggleLink.setDisable(false);
                            errorLabel.setText("Auth ok, but TCP server rejected username or connection failed.");
                        }
                    });
                } else {
                    Platform.runLater(() -> {
                        actionBtn.setDisable(false);
                        toggleLink.setDisable(false);
                        errorLabel.setText("Login failed: " + loginRes.errorMessage);
                    });
                }
            });
        });

        // Trigger on enter key
        passwordField.setOnAction(e -> actionBtn.fire());
        usernameField.setOnAction(e -> actionBtn.fire());

        card.getChildren().addAll(title, subtitle, fieldsBox, actionBtn, toggleLink, errorLabel);
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
            scene.getStylesheets().add(getClass().getResource("/css/" + currentTheme).toExternalForm());
        } catch (Exception e) {
            System.err.println("Could not load " + currentTheme);
        }
        
        primaryStage.setTitle("Chat Application Auth");
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
        
        Region sidebarSpacer = new Region();
        VBox.setVgrow(sidebarSpacer, Priority.ALWAYS);
        
        Button settingsBtn = new Button(" Settings");
        settingsBtn.setGraphic(IconFactory.getIcon("settings", "#FFFFFF", 14));
        settingsBtn.getStyleClass().add("btn-primary");
        settingsBtn.setMaxWidth(Double.MAX_VALUE);
        settingsBtn.setOnAction(e -> showSettingsDialog());
        leftSidebar.getChildren().addAll(sidebarSpacer, settingsBtn);
        leftSidebar.setPadding(new Insets(0, 0, 15, 0));
        
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

        // Microphone Selector in Top Bar
        ComboBox<String> micBox = new ComboBox<>();
        micBox.setPromptText("Select Microphone");
        micBox.setPrefWidth(200);

        // Helper to populate mic list from live system devices
        Runnable refreshMics = () -> {
            String currentVal = micBox.getValue();
            java.util.List<String> live = AudioClient.getAvailableMicrophones();
            java.util.List<String> items = new java.util.ArrayList<>();
            items.add("Default Mic");
            items.addAll(live);

            // Only update if the list actually changed (avoids unnecessary UI flicker)
            if (!items.equals(micBox.getItems())) {
                micBox.getItems().setAll(items);
                if (currentVal != null && items.contains(currentVal)) {
                    micBox.setValue(currentVal); // keep selection
                } else {
                    micBox.setValue("Default Mic");
                }
                micBox.setDisable(live.isEmpty());
                System.out.println("[Audio] Device list refreshed: " + live);
            }
        };
        refreshMics.run(); // initial populate

        micBox.setOnAction(e -> {
            String selected = micBox.getValue();
            boolean isDefault = selected == null || "Default Mic".equals(selected);
            audioClient.setMicrophone(isDefault ? null : selected);
            audioRecorder.setMicrophone(isDefault ? null : selected); // also applies to voice notes
        });

        // Refresh button — queries live devices on demand
        Button micRefreshBtn = new Button("🔄");
        micRefreshBtn.setStyle("-fx-background-color: #272A30; -fx-text-fill: white; -fx-background-radius: 6; -fx-cursor: hand; -fx-font-size: 13px; -fx-padding: 4 8;");
        micRefreshBtn.setTooltip(new Tooltip("Refresh microphone list"));
        micRefreshBtn.setOnAction(e -> {
            refreshMics.run();
            micRefreshBtn.setText("✓");
            new Timeline(new KeyFrame(Duration.millis(1200), ev -> micRefreshBtn.setText("🔄"))).play();
        });

        // Background device monitor: auto-refreshes every 3 s when a new device is plugged in
        Timeline deviceMonitor = new Timeline(new KeyFrame(Duration.seconds(3), ev -> refreshMics.run()));
        deviceMonitor.setCycleCount(Timeline.INDEFINITE);
        deviceMonitor.play();

        voiceCallBtn = new Button(" Join Voice Channel");
        voiceCallBtn.setGraphic(IconFactory.getIcon("phone", "#FFFFFF", 14));
        voiceCallBtn.getStyleClass().add("btn-primary");
        voiceCallBtn.setOnAction(e -> toggleVoiceCall());

        topBar.getChildren().addAll(topBarTitle, spacer, micBox, micRefreshBtn, voiceCallBtn);
        topBar.setSpacing(10);
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
        HBox inputArea = new HBox(10);
        inputArea.getStyleClass().add("input-area");
        inputArea.setAlignment(Pos.CENTER_LEFT);
        
        attachBtn = new Button();
        attachBtn.setGraphic(IconFactory.getIcon("paperclip", "#b9bbbe", 18));
        attachBtn.setStyle("-fx-background-color: #272A30; -fx-background-radius: 50%; -fx-min-width: 38px; -fx-min-height: 38px; -fx-cursor: hand; -fx-padding: 0;");
        
        emojiBtn = new Button();
        emojiBtn.setGraphic(IconFactory.getIcon("smile", "#b9bbbe", 18));
        emojiBtn.setStyle("-fx-background-color: #272A30; -fx-background-radius: 50%; -fx-min-width: 38px; -fx-min-height: 38px; -fx-cursor: hand; -fx-padding: 0;");
        
        recordBtn = new Button();
        recordBtn.setGraphic(IconFactory.getIcon("mic", "#b9bbbe", 18));
        recordBtn.setStyle("-fx-background-color: #272A30; -fx-background-radius: 50%; -fx-min-width: 38px; -fx-min-height: 38px; -fx-cursor: hand; -fx-padding: 0;");
        
        messageField = new TextField();
        messageField.getStyleClass().add("chat-text-field");
        messageField.setPromptText("Message #global-chat");
        HBox.setHgrow(messageField, Priority.ALWAYS);
        messageField.setOnAction(e -> sendMessage());
        
        // Recording UI Placeholder
        recordingInputPlaceholder = new HBox(15);
        recordingInputPlaceholder.setAlignment(Pos.CENTER_LEFT);
        recordingInputPlaceholder.setStyle("-fx-background-color: #1A1D21; -fx-background-radius: 20; -fx-padding: 0 20 0 20; -fx-border-color: #FF4A4A; -fx-border-radius: 20; -fx-min-height: 38px;");
        recordingInputPlaceholder.setVisible(false);
        recordingInputPlaceholder.setManaged(false);
        HBox.setHgrow(recordingInputPlaceholder, Priority.ALWAYS);
        
        recordingDot = new Circle(4, javafx.scene.paint.Color.web("#FF4A4A"));
        
        recordingTimerLabel = new Label("Recording: 0.0s");
        recordingTimerLabel.setStyle("-fx-text-fill: #FF4A4A; -fx-font-weight: bold; -fx-font-size: 13px;");
        
        visualizerContainer = new HBox(4);
        visualizerContainer.setAlignment(Pos.CENTER_LEFT);
        for (int i = 0; i < 6; i++) {
            Rectangle r = new Rectangle(4, 10, javafx.scene.paint.Color.web("#FF4A4A"));
            r.setArcWidth(2);
            r.setArcHeight(2);
            visualizerContainer.getChildren().add(r);
        }
        
        Label instructionLabel = new Label("Click mic again to stop and send");
        instructionLabel.setStyle("-fx-text-fill: #666D80; -fx-font-style: italic; -fx-font-size: 12px;");
        
        recordingInputPlaceholder.getChildren().addAll(recordingDot, recordingTimerLabel, visualizerContainer, instructionLabel);
        
        // Managed property bindings
        messageField.visibleProperty().bind(recordingInputPlaceholder.visibleProperty().not());
        messageField.managedProperty().bind(messageField.visibleProperty());
        
        // Setup Attachment Actions
        ContextMenu attachMenu = new ContextMenu();
        MenuItem uploadImg = new MenuItem("Upload Image 📷");
        MenuItem uploadVid = new MenuItem("Upload Video 🎥");
        attachMenu.getItems().addAll(uploadImg, uploadVid);
        attachBtn.setOnAction(e -> attachMenu.show(attachBtn, javafx.geometry.Side.TOP, 0, 0));
        uploadImg.setOnAction(e -> selectAndUploadFile("image"));
        uploadVid.setOnAction(e -> selectAndUploadFile("video"));
        
        // Setup Emoji Picker
        ContextMenu emojiMenu = new ContextMenu();
        GridPane emojiGrid = new GridPane();
        emojiGrid.setHgap(8);
        emojiGrid.setVgap(8);
        emojiGrid.setPadding(new Insets(10));
        emojiGrid.setStyle("-fx-background-color: #1A1D21; -fx-border-color: #272A30; -fx-border-width: 1; -fx-border-radius: 8; -fx-background-radius: 8;");
        
        String[] emojis = {
            "😊", "😂", "🤣", "❤️", "👍", "🔥", "🎉", "🚀",
            "😢", "😍", "🤔", "😎", "👏", "🙌", "💩", "💡",
            "💯", "✨", "🙏", "👀", "💻", "🎨", "🎙️", "🎥"
        };
        int cols = 6;
        for (int i = 0; i < emojis.length; i++) {
            final String emoji = emojis[i];
            Button btn = new Button(emoji);
            btn.setStyle("-fx-background-color: transparent; -fx-text-fill: white; -fx-font-size: 16px; -fx-cursor: hand; -fx-padding: 4;");
            btn.setOnMouseEntered(ev -> btn.setStyle("-fx-background-color: #272A30; -fx-text-fill: white; -fx-font-size: 16px; -fx-cursor: hand; -fx-padding: 4; -fx-background-radius: 4;"));
            btn.setOnMouseExited(ev -> btn.setStyle("-fx-background-color: transparent; -fx-text-fill: white; -fx-font-size: 16px; -fx-cursor: hand; -fx-padding: 4;"));
            btn.setOnAction(ev -> {
                messageField.appendText(emoji);
                messageField.requestFocus();
                emojiMenu.hide();
            });
            int row = i / cols;
            int col = i % cols;
            emojiGrid.add(btn, col, row);
        }
        CustomMenuItem customItem = new CustomMenuItem(emojiGrid);
        customItem.setHideOnClick(false);
        emojiMenu.getItems().add(customItem);
        emojiBtn.setOnAction(e -> emojiMenu.show(emojiBtn, javafx.geometry.Side.TOP, 0, 0));
        
        // Setup Voice Recording Action
        recordBtn.setOnAction(e -> toggleRecording());
        
        inputArea.getChildren().addAll(attachBtn, emojiBtn, messageField, recordingInputPlaceholder, recordBtn);
        
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
                    
                    String avatarUrl = item.equals(currentUsername) ? currentAvatarUrl : "https://api.dicebear.com/7.x/initials/png?seed=" + item + "&backgroundColor=1a1b1e";
                    javafx.scene.image.ImageView avatar = new javafx.scene.image.ImageView(new javafx.scene.image.Image(avatarUrl, 24, 24, true, true));
                    Circle clip = new Circle(12, 12, 12);
                    avatar.setClip(clip);
                    
                    Label nameLabel = new Label(item);
                    nameLabel.setStyle("-fx-text-fill: #EFEFEF; -fx-font-size: 14px;");
                    
                    Region spacer = new Region();
                    HBox.setHgrow(spacer, Priority.ALWAYS);
                    
                    Button dmBtn = new Button("DM");
                    dmBtn.getStyleClass().add("dm-btn");
                    dmBtn.setOnAction(e -> {
                        if (!item.equals(currentUsername)) {
                            openDirectMessage(item);
                        }
                    });
                    
                    if (item.equals(currentUsername)) {
                        box.getChildren().addAll(statusDot, avatar, nameLabel);
                    } else {
                        box.getChildren().addAll(statusDot, avatar, nameLabel, spacer, dmBtn);
                    }
                    
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
            scene.getStylesheets().add(getClass().getResource("/css/" + currentTheme).toExternalForm());
        } catch (Exception e) {
            System.err.println("Could not load " + currentTheme);
        }
        
        applyWallpaper();
        
        primaryStage.setTitle("Discord-like Chat Application");
        primaryStage.setScene(scene);
        
        primaryStage.setOnCloseRequest(e -> {
            chatClient.close();
            audioClient.stopCall();
            executorService.shutdownNow();
            System.exit(0);
        });
        
        // Load past DM partners and initially load global chat history
        if (SupabaseService.isConfigured()) {
            executorService.submit(() -> {
                List<String> dmPartners = SupabaseService.getPastDMPartners(currentUsername);
                Platform.runLater(() -> {
                    for (String partner : dmPartners) {
                        if (!chatHistories.containsKey(partner)) {
                            chatHistories.put(partner, new ArrayList<>());
                            if (!dmList.getItems().contains(partner)) {
                                dmList.getItems().add(partner);
                            }
                        }
                    }
                    // Load global chat history
                    switchChat("Global");
                });
            });
        } else {
            switchChat("Global");
        }
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
        
        messageContainer.getChildren().clear();
        
        if (SupabaseService.isConfigured()) {
            Label loadingLabel = new Label("Loading message history...");
            loadingLabel.setStyle("-fx-text-fill: #666D80; -fx-padding: 15; -fx-font-style: italic;");
            messageContainer.getChildren().add(loadingLabel);
            
            executorService.submit(() -> {
                try {
                    List<SupabaseService.Message> dbMessages = SupabaseService.getMessages(currentUsername, chatName);
                    Platform.runLater(() -> {
                        messageContainer.getChildren().clear();
                        chatHistories.put(chatName, new ArrayList<>());
                        for (SupabaseService.Message dbMsg : dbMessages) {
                            appendToHistory(chatName, dbMsg.sender, dbMsg.content, dbMsg.media_url, dbMsg.media_type);
                        }
                    });
                } catch (Exception ex) {
                    System.err.println("Error loading chat history: " + ex.getMessage());
                    Platform.runLater(() -> {
                        messageContainer.getChildren().clear();
                        renderChat(chatName);
                    });
                }
            });
        } else {
            renderChat(chatName);
        }
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

    private MediaPlayer activeVoicePlayer = null;

    private HBox createMessageBubble(String sender, String text, String mediaUrl, String mediaType) {
        VBox bubble = new VBox(2);
        
        Label senderLabel = new Label(sender);
        senderLabel.getStyleClass().add("message-sender");
        senderLabel.setStyle("-fx-text-fill: " + getUsernameColor(sender) + ";");
        bubble.getChildren().add(senderLabel);
        
        if (text != null && !text.isEmpty()) {
            Label textLabel = new Label(text);
            textLabel.getStyleClass().add("message-text");
            bubble.getChildren().add(textLabel);
        }
        
        // Render Media if present
        if (mediaUrl != null && !mediaUrl.isEmpty()) {
            if ("image".equals(mediaType)) {
                ImageView imageView = new ImageView();
                imageView.setFitWidth(260);
                imageView.setPreserveRatio(true);
                imageView.setStyle("-fx-cursor: hand; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.15), 5, 0, 0, 1); -fx-background-radius: 8;");
                
                try {
                    Image img = new Image(mediaUrl, true);
                    imageView.setImage(img);
                } catch (Exception ex) {
                    System.err.println("Error loading chat image: " + ex.getMessage());
                }
                
                imageView.setOnMouseClicked(event -> showLightbox(mediaUrl));
                bubble.getChildren().add(imageView);
            } else if ("video".equals(mediaType)) {
                StackPane videoPlaceholder = new StackPane();
                videoPlaceholder.setPrefSize(260, 140);
                videoPlaceholder.setStyle("-fx-background-color: #1A1D21; -fx-background-radius: 8; -fx-cursor: hand; -fx-border-color: #272A30; -fx-border-width: 1; -fx-border-radius: 8;");
                
                Group playIcon = IconFactory.getIcon("play", "#0084FF", 28);
                
                Label playText = new Label("Play Video");
                playText.setStyle("-fx-text-fill: #666D80; -fx-font-size: 11px; -fx-font-weight: bold;");
                
                VBox box = new VBox(8, playIcon, playText);
                box.setAlignment(Pos.CENTER);
                videoPlaceholder.getChildren().add(box);
                
                videoPlaceholder.setOnMouseClicked(event -> showVideoPlayer(mediaUrl));
                bubble.getChildren().add(videoPlaceholder);
            } else if ("audio".equals(mediaType)) {
                HBox audioPlayer = new HBox(8);
                audioPlayer.setAlignment(Pos.CENTER_LEFT);
                audioPlayer.setPadding(new Insets(6, 12, 6, 12));
                audioPlayer.setStyle("-fx-background-color: #1A1D21; -fx-background-radius: 16; -fx-border-color: #272A30; -fx-border-width: 1; -fx-border-radius: 16;");
                audioPlayer.setPrefWidth(260);
                
                Button playBtn = new Button();
                playBtn.setGraphic(IconFactory.getIcon("play", "#FFFFFF", 12));
                playBtn.setStyle("-fx-background-color: #0084FF; -fx-background-radius: 50%; -fx-min-width: 28px; -fx-min-height: 28px; -fx-cursor: hand; -fx-padding: 0;");
                
                Slider progressSlider = new Slider(0, 100, 0);
                HBox.setHgrow(progressSlider, Priority.ALWAYS);
                progressSlider.setDisable(true);
                
                Label timeLabel = new Label("0:00 / 0:00");
                timeLabel.setStyle("-fx-text-fill: #666D80; -fx-font-size: 11px; -fx-font-weight: bold;");
                
                audioPlayer.getChildren().addAll(playBtn, progressSlider, timeLabel);
                bubble.getChildren().add(audioPlayer);
                
                final MediaPlayer[] playerRef = new MediaPlayer[1];
                
                playBtn.setOnAction(e -> {
                    if (playerRef[0] == null) {
                        playBtn.setDisable(true);
                        playBtn.setGraphic(null);
                        playBtn.setText("⏳");
                        
                        executorService.submit(() -> {
                            try {
                                Media media = new Media(mediaUrl);
                                MediaPlayer mediaPlayer = new MediaPlayer(media);
                                playerRef[0] = mediaPlayer;
                                
                                mediaPlayer.setOnReady(() -> Platform.runLater(() -> {
                                    playBtn.setDisable(false);
                                    playBtn.setText("");
                                    playBtn.setGraphic(IconFactory.getIcon("play", "#FFFFFF", 12));
                                    progressSlider.setDisable(false);
                                    progressSlider.setMax(mediaPlayer.getTotalDuration().toSeconds());
                                    
                                    progressSlider.valueChangingProperty().addListener((obs, wasChanging, isChanging) -> {
                                        if (!isChanging) {
                                            mediaPlayer.seek(Duration.seconds(progressSlider.getValue()));
                                        }
                                    });
                                    progressSlider.setOnMouseClicked(me -> {
                                        mediaPlayer.seek(Duration.seconds(progressSlider.getValue()));
                                    });
                                }));
                                
                                mediaPlayer.currentTimeProperty().addListener((obs, oldTime, newTime) -> Platform.runLater(() -> {
                                    if (!progressSlider.isValueChanging()) {
                                        progressSlider.setValue(newTime.toSeconds());
                                    }
                                    timeLabel.setText(formatTime(newTime) + " / " + formatTime(mediaPlayer.getTotalDuration()));
                                }));
                                
                                mediaPlayer.setOnEndOfMedia(() -> Platform.runLater(() -> {
                                    mediaPlayer.stop();
                                    playBtn.setText("");
                                    playBtn.setGraphic(IconFactory.getIcon("play", "#FFFFFF", 12));
                                    progressSlider.setValue(0);
                                }));
                                
                                Platform.runLater(() -> togglePlayState(mediaPlayer, playBtn));
                            } catch (Exception ex) {
                                System.err.println("Audio load error: " + ex.getMessage());
                                Platform.runLater(() -> {
                                    playBtn.setDisable(false);
                                    playBtn.setText("❌");
                                    playBtn.setGraphic(null);
                                });
                            }
                        });
                    } else {
                        togglePlayState(playerRef[0], playBtn);
                    }
                });
            } else if ("uploading".equals(mediaType)) {
                Label uploadLabel = new Label(text);
                uploadLabel.setStyle("-fx-text-fill: #b9bbbe; -fx-font-style: italic; -fx-font-size: 13px;");
                bubble.getChildren().clear();
                bubble.getChildren().addAll(senderLabel, uploadLabel);
            }
        }
        
        HBox container = new HBox(10);
        container.setPadding(new Insets(5, 20, 5, 20));
        
        javafx.scene.image.ImageView avatar = null;
        if (!sender.equals("[SERVER]")) {
            String avatarUrl = sender.equals(currentUsername) ? currentAvatarUrl : "https://api.dicebear.com/7.x/initials/png?seed=" + sender + "&backgroundColor=2f3136";
            try {
                avatar = new javafx.scene.image.ImageView(new javafx.scene.image.Image(avatarUrl, 32, 32, true, true));
            } catch (Exception e) {
                avatar = new javafx.scene.image.ImageView(new javafx.scene.image.Image("https://api.dicebear.com/7.x/initials/png?seed=" + sender + "&backgroundColor=2f3136", 32, 32, true, true));
            }
            Circle clip = new Circle(16, 16, 16);
            avatar.setClip(clip);
        }

        if (sender.equals(currentUsername)) {
            bubble.getStyleClass().add("message-bubble-self");
            container.setAlignment(Pos.CENTER_RIGHT);
            if (avatar != null) container.getChildren().addAll(bubble, avatar);
            else container.getChildren().add(bubble);
        } else {
            bubble.getStyleClass().add("message-bubble-other");
            container.setAlignment(Pos.CENTER_LEFT);
            if (avatar != null) container.getChildren().addAll(avatar, bubble);
            else container.getChildren().add(bubble);
        }
        
        return container;
    }

    private void appendToHistory(String chatName, String sender, String text, String mediaUrl, String mediaType) {
        chatHistories.putIfAbsent(chatName, new ArrayList<>());
        HBox bubble = createMessageBubble(sender, text, mediaUrl, mediaType);
        chatHistories.get(chatName).add(bubble);
        
        if (currentActiveChat.equals(chatName)) {
            messageContainer.getChildren().add(bubble);
        }
    }

    private void sendMessage() {
        String msg = messageField.getText().trim();
        if (!msg.isEmpty()) {
            final String finalMsg = msg;
            final String target = currentActiveChat;
            
            // Save to DB asynchronously
            if (SupabaseService.isConfigured()) {
                executorService.submit(() -> {
                    SupabaseService.saveMessage(currentUsername, target, finalMsg, null, null);
                });
            }

            if (target.equals("Global")) {
                chatClient.sendMessage(finalMsg);
                appendToHistory("Global", currentUsername, finalMsg, null, null);
            } else {
                chatClient.sendPrivateMessage(target, finalMsg);
                appendToHistory(target, currentUsername, finalMsg, null, null);
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
                String sender = message.substring(14, endBracket);
                String actualMsg = message.substring(endBracket + 3);
                
                // Ensure DM exists in list
                if (!chatHistories.containsKey(sender)) {
                    chatHistories.put(sender, new ArrayList<>());
                    dmList.getItems().add(sender);
                }
                parseAndAppendMessage(sender, sender, actualMsg);
            } catch (Exception e) {
                appendToHistory("Global", "[SERVER]", message, null, null);
            }
        } else if (message.startsWith("[SERVER]") || message.startsWith("[Server]")) {
            appendToHistory("Global", "[SERVER]", message.substring(8).trim(), null, null);
        } else {
            int colonIdx = message.indexOf(":");
            if (colonIdx != -1) {
                String sender = message.substring(0, colonIdx);
                String actualMsg = message.substring(colonIdx + 1).trim();
                parseAndAppendMessage("Global", sender, actualMsg);
            } else {
                appendToHistory("Global", "[SERVER]", message, null, null);
            }
        }
    }

    private void parseAndAppendMessage(String chatName, String sender, String body) {
        if (body.startsWith("/media ")) {
            try {
                String[] parts = body.split(" ", 4);
                if (parts.length >= 3) {
                    String mediaType = parts[1];
                    String mediaUrl = parts[2];
                    String caption = (parts.length == 4) ? parts[3] : "";
                    appendToHistory(chatName, sender, caption, mediaUrl, mediaType);
                    return;
                }
            } catch (Exception e) {
                System.err.println("Failed to parse media message: " + body);
            }
        }
        appendToHistory(chatName, sender, body, null, null);
    }

    private void sendMediaMessage(String mediaUrl, String mediaType, String caption) {
        final String target = currentActiveChat;
        final String content = "/media " + mediaType + " " + mediaUrl + " " + caption;

        // Save to DB asynchronously
        if (SupabaseService.isConfigured()) {
            executorService.submit(() -> {
                SupabaseService.saveMessage(currentUsername, target, caption, mediaUrl, mediaType);
            });
        }

        if (target.equals("Global")) {
            chatClient.sendMessage(content);
            appendToHistory("Global", currentUsername, caption, mediaUrl, mediaType);
        } else {
            chatClient.sendPrivateMessage(target, content);
            appendToHistory(target, currentUsername, caption, mediaUrl, mediaType);
        }
    }

    private void selectAndUploadFile(String type) {
        FileChooser chooser = new FileChooser();
        if ("image".equals(type)) {
            chooser.setTitle("Select Image");
            chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif")
            );
        } else if ("video".equals(type)) {
            chooser.setTitle("Select Video");
            chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Videos", "*.mp4", "*.mkv", "*.avi")
            );
        }
        
        File file = chooser.showOpenDialog(primaryStage);
        if (file != null) {
            uploadAndSendMedia(file, type);
        }
    }

    private void uploadAndSendMedia(File file, String type) {
        final String captionPrompt = "Sending " + type + "...";
        Platform.runLater(() -> {
            appendToHistory(currentActiveChat, currentUsername, captionPrompt, file.getName(), "uploading");
        });
        
        executorService.submit(() -> {
            File fileToUpload = file;
            if ("video".equals(type)) {
                fileToUpload = VideoCompressor.compressVideo(file);
            }
            
            String remotePath = type + "s/" + System.currentTimeMillis() + "_" + fileToUpload.getName();
            String url = SupabaseService.uploadFile("chat-media", remotePath, fileToUpload);
            
            // Cleanup compressed files and recorded clips
            if (fileToUpload != file) {
                fileToUpload.delete();
            }
            if ("audio".equals(type)) {
                file.delete();
            }
            
            Platform.runLater(() -> {
                // Remove the uploading placeholder
                messageContainer.getChildren().removeIf(node -> isSendingPlaceholder(node, captionPrompt));
                
                if (url != null) {
                    sendMediaMessage(url, type, "");
                } else {
                    String guidance = "Upload failed!\n\n" +
                        "Check the following in your Supabase project:\n" +
                        "1. Storage bucket 'chat-media' exists\n" +
                        "2. The bucket is set to PUBLIC\n" +
                        "3. Anon role has INSERT policy on storage.objects\n\n" +
                        "See the console output for the exact error.";
                    Alert alert = new Alert(Alert.AlertType.ERROR, guidance);
                    alert.setTitle("File Upload Failed");
                    alert.setHeaderText("Cannot upload " + type);
                    alert.getDialogPane().setPrefWidth(450);
                    alert.show();
                }
            });
        });
    }

    private boolean isSendingPlaceholder(javafx.scene.Node node, String text) {
        if (!(node instanceof HBox)) return false;
        HBox container = (HBox) node;
        for (javafx.scene.Node child : container.getChildren()) {
            if (child instanceof VBox) {
                VBox bubble = (VBox) child;
                for (javafx.scene.Node bubbleChild : bubble.getChildren()) {
                    if (bubbleChild instanceof Label) {
                        Label lbl = (Label) bubbleChild;
                        if (text.equals(lbl.getText())) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private void toggleRecording() {
        if (!audioRecorder.isRecording()) {
            try {
                audioRecorder.startRecording();
                
                recordingInputPlaceholder.setManaged(true);
                recordingInputPlaceholder.setVisible(true);
                recordBtn.setGraphic(IconFactory.getIcon("x", "#FF4A4A", 18));
                attachBtn.setDisable(true);
                emojiBtn.setDisable(true);
                
                // Pulsing dot animation
                recordPulseTransition = new FadeTransition(Duration.millis(600), recordingDot);
                recordPulseTransition.setFromValue(1.0);
                recordPulseTransition.setToValue(0.15);
                recordPulseTransition.setCycleCount(FadeTransition.INDEFINITE);
                recordPulseTransition.setAutoReverse(true);
                recordPulseTransition.play();
                
                recordingSeconds = 0;
                recordingTimeline = new Timeline(new KeyFrame(Duration.millis(100), ev -> {
                    recordingSeconds += 100;
                    int sec = recordingSeconds / 1000;
                    int ms = (recordingSeconds % 1000) / 100;
                    recordingTimerLabel.setText(String.format("Recording: %d.%ds", sec, ms));
                    
                    // Animate visualizer
                    double amp = audioRecorder.getCurrentAmplitude();
                    for (int i = 0; i < visualizerContainer.getChildren().size(); i++) {
                        Rectangle r = (Rectangle) visualizerContainer.getChildren().get(i);
                        double height = 6 + (amp * 26) * (0.6 + Math.random() * 0.4);
                        r.setHeight(Math.max(6, Math.min(32, height)));
                    }
                }));
                recordingTimeline.setCycleCount(Timeline.INDEFINITE);
                recordingTimeline.play();
            } catch (Exception ex) {
                System.err.println("Recording failed to start: " + ex.getMessage());
                Alert alert = new Alert(Alert.AlertType.ERROR, "Failed to start recording: " + ex.getMessage());
                alert.show();
            }
        } else {
            if (recordingTimeline != null) {
                recordingTimeline.stop();
            }
            if (recordPulseTransition != null) {
                recordPulseTransition.stop();
                recordingDot.setOpacity(1.0);
            }
            
            File audioFile = audioRecorder.stopRecording();
            
            recordingInputPlaceholder.setVisible(false);
            recordingInputPlaceholder.setManaged(false);
            recordBtn.setGraphic(IconFactory.getIcon("mic", "#b9bbbe", 18));
            attachBtn.setDisable(false);
            emojiBtn.setDisable(false);
            
            if (audioFile != null && audioFile.exists()) {
                uploadAndSendMedia(audioFile, "audio");
            }
        }
    }

    private void showLightbox(String imageUrl) {
        Stage stage = new Stage();
        stage.initOwner(primaryStage);
        stage.setTitle("Image Preview");
        
        StackPane root = new StackPane();
        root.setStyle("-fx-background-color: rgba(0, 0, 0, 0.9);");
        
        ImageView view = new ImageView();
        view.setPreserveRatio(true);
        view.fitWidthProperty().bind(stage.widthProperty().multiply(0.9));
        view.fitHeightProperty().bind(stage.heightProperty().multiply(0.9));
        
        try {
            view.setImage(new Image(imageUrl, true));
        } catch (Exception e) {
            System.err.println("Failed to load lightbox image: " + e.getMessage());
        }
        
        Button closeBtn = new Button();
        closeBtn.setGraphic(IconFactory.getIcon("x", "#FFFFFF", 16));
        closeBtn.setStyle("-fx-background-color: rgba(255, 255, 255, 0.25); -fx-background-radius: 50%; -fx-min-width: 36px; -fx-min-height: 36px; -fx-cursor: hand; -fx-padding: 0;");
        StackPane.setAlignment(closeBtn, Pos.TOP_RIGHT);
        StackPane.setMargin(closeBtn, new Insets(20));
        closeBtn.setOnAction(e -> stage.close());
        
        root.getChildren().addAll(view, closeBtn);
        root.setOnMouseClicked(e -> {
            if (e.getTarget() == root || e.getTarget() == view) {
                stage.close();
            }
        });
        
        Scene scene = new Scene(root, 800, 600);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        stage.setScene(scene);
        stage.show();
    }

    private void showVideoPlayer(String videoUrl) {
        Stage stage = new Stage();
        stage.initOwner(primaryStage);
        stage.setTitle("Video Player");

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #0b0c0f;");

        try {
            Media media = new Media(videoUrl);
            MediaPlayer player = new MediaPlayer(media);
            MediaView mediaView = new MediaView(player);
            
            mediaView.fitWidthProperty().bind(root.widthProperty());
            mediaView.fitHeightProperty().bind(root.heightProperty().subtract(60));
            mediaView.setPreserveRatio(true);

            StackPane viewContainer = new StackPane(mediaView);
            viewContainer.setAlignment(Pos.CENTER);
            root.setCenter(viewContainer);

            HBox controls = new HBox(12);
            controls.setAlignment(Pos.CENTER_LEFT);
            controls.setPadding(new Insets(10, 15, 10, 15));
            controls.setStyle("-fx-background-color: #131619;");

            Button playBtn = new Button();
            playBtn.setGraphic(IconFactory.getIcon("play", "#FFFFFF", 12));
            playBtn.setStyle("-fx-background-color: #0084FF; -fx-background-radius: 8; -fx-cursor: hand; -fx-min-width: 30px; -fx-min-height: 30px; -fx-padding: 0;");
            
            Slider progress = new Slider(0, 100, 0);
            HBox.setHgrow(progress, Priority.ALWAYS);
            progress.setDisable(true);

            Label durationLabel = new Label("0:00 / 0:00");
            durationLabel.setStyle("-fx-text-fill: white;");

            Label volIcon = new Label("🔊");
            volIcon.setStyle("-fx-text-fill: white;");
            Slider volSlider = new Slider(0, 1.0, 0.8);
            volSlider.setPrefWidth(80);
            player.volumeProperty().bind(volSlider.valueProperty());

            controls.getChildren().addAll(playBtn, progress, durationLabel, volIcon, volSlider);
            root.setBottom(controls);

            player.setOnReady(() -> Platform.runLater(() -> {
                progress.setDisable(false);
                progress.setMax(player.getTotalDuration().toSeconds());
                durationLabel.setText("0:00 / " + formatTime(player.getTotalDuration()));
                player.play();
                playBtn.setText("");
                playBtn.setGraphic(IconFactory.getIcon("pause", "#FFFFFF", 12));
            }));

            playBtn.setOnAction(e -> {
                if (player.getStatus() == MediaPlayer.Status.PLAYING) {
                    player.pause();
                    playBtn.setText("");
                    playBtn.setGraphic(IconFactory.getIcon("play", "#FFFFFF", 12));
                } else {
                    player.play();
                    playBtn.setText("");
                    playBtn.setGraphic(IconFactory.getIcon("pause", "#FFFFFF", 12));
                }
            });

            player.currentTimeProperty().addListener((obs, oldTime, newTime) -> Platform.runLater(() -> {
                if (!progress.isValueChanging()) {
                    progress.setValue(newTime.toSeconds());
                }
                durationLabel.setText(formatTime(newTime) + " / " + formatTime(player.getTotalDuration()));
            }));

            progress.valueChangingProperty().addListener((obs, wasChanging, isChanging) -> {
                if (!isChanging) {
                    player.seek(Duration.seconds(progress.getValue()));
                }
            });
            progress.setOnMouseClicked(me -> {
                player.seek(Duration.seconds(progress.getValue()));
            });

            stage.setOnCloseRequest(e -> {
                player.stop();
                player.dispose();
            });

            Scene scene = new Scene(root, 640, 420);
            stage.setScene(scene);
            stage.show();
        } catch (Exception ex) {
            System.err.println("Failed to build video player UI: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private String formatTime(Duration duration) {
        if (duration == null || duration.isUnknown()) return "0:00";
        int seconds = (int) Math.floor(duration.toSeconds());
        int minutes = seconds / 60;
        int remainingSeconds = seconds % 60;
        return String.format("%d:%02d", minutes, remainingSeconds);
    }

    private void togglePlayState(MediaPlayer mediaPlayer, Button playBtn) {
        if (mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
            mediaPlayer.pause();
            playBtn.setGraphic(IconFactory.getIcon("play", "#FFFFFF", 12));
        } else {
            if (activeVoicePlayer != null && activeVoicePlayer != mediaPlayer) {
                activeVoicePlayer.pause();
            }
            activeVoicePlayer = mediaPlayer;
            mediaPlayer.play();
            playBtn.setGraphic(IconFactory.getIcon("pause", "#FFFFFF", 12));
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
            audioClient.startCall(serverIp, currentUsername);
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

    private void showSettingsDialog() {
        Stage settingsStage = new Stage();
        settingsStage.setTitle("Settings");
        
        VBox root = new VBox(12); // Reduced from 15
        root.setPadding(new Insets(15)); // Reduced from 20
        root.getStyleClass().add("login-bg");
        
        Label title = new Label("Application Settings");
        title.setStyle("-fx-font-size: 20px; -fx-text-fill: #0084FF; -fx-font-weight: bold;"); // Colored for visibility in both themes
        
        // Microphone
        Label micLabel = new Label("Audio Input (Microphone):");
        micLabel.getStyleClass().add("header-label");
        ComboBox<String> micBox = new ComboBox<>();
        micBox.setMaxWidth(Double.MAX_VALUE);
        micBox.getItems().addAll(AudioClient.getAvailableMicrophones());
        if (micBox.getItems().isEmpty()) {
            micBox.getItems().add("No Microphone Found");
            micBox.setDisable(true);
        } else {
            micBox.getItems().add(0, "Default");
            micBox.setValue("Default");
        }
        micBox.setOnAction(e -> {
            String selected = micBox.getValue();
            if ("Default".equals(selected) || "No Microphone Found".equals(selected)) {
                audioClient.setMicrophone(null);
            } else {
                audioClient.setMicrophone(selected);
            }
        });
        VBox micSection = new VBox(5, micLabel, micBox);
        
        // Theme
        Label themeLabel = new Label("Theme:");
        themeLabel.getStyleClass().add("header-label");
        ComboBox<String> themeBox = new ComboBox<>();
        themeBox.getItems().addAll("Dark", "Light", "Ocean");
        themeBox.setValue(currentTheme.replace("-theme.css", "").substring(0, 1).toUpperCase() + currentTheme.replace("-theme.css", "").substring(1));
        themeBox.setOnAction(e -> {
            currentTheme = themeBox.getValue().toLowerCase() + "-theme.css";
            applyTheme();
            // Re-apply to settings stage as well
            if (settingsStage.getScene() != null) {
                settingsStage.getScene().getStylesheets().clear();
                try {
                    settingsStage.getScene().getStylesheets().add(getClass().getResource("/css/" + currentTheme).toExternalForm());
                } catch (Exception ex) {}
            }
        });
        
        // Wallpaper
        Label wallpaperLabel = new Label("Wallpaper URL (leave blank for none):");
        wallpaperLabel.getStyleClass().add("header-label");
        TextField wallpaperField = new TextField(currentWallpaper);
        wallpaperField.getStyleClass().add("chat-text-field");
        wallpaperField.textProperty().addListener((obs, oldVal, newVal) -> {
            currentWallpaper = newVal.trim();
            applyWallpaper();
        });
        
        // Quick Pexels Wallpaper presets
        HBox wpPresetsBox = new HBox(10);
        String[] wpPresets = {
            "https://images.pexels.com/photos/1103970/pexels-photo-1103970.jpeg?auto=compress&cs=tinysrgb&w=100", // abstract
            "https://images.pexels.com/photos/1307698/pexels-photo-1307698.jpeg?auto=compress&cs=tinysrgb&w=100", // stars
            "https://images.pexels.com/photos/255379/pexels-photo-255379.jpeg?auto=compress&cs=tinysrgb&w=100", // ocean
            "https://images.pexels.com/photos/1311590/pexels-photo-1311590.jpeg?auto=compress&cs=tinysrgb&w=100"  // neon
        };
        for (String url : wpPresets) {
            try {
                javafx.scene.image.ImageView img = new javafx.scene.image.ImageView(new javafx.scene.image.Image(url, 40, 25, true, true));
                Button btn = new Button();
                btn.setGraphic(img);
                btn.setStyle("-fx-background-color: transparent; -fx-cursor: hand; -fx-padding: 0;");
                btn.setOnAction(e -> {
                    // Remove the width restriction when setting the actual wallpaper
                    String fullResUrl = url.replace("&w=100", "&w=1260&h=750&dpr=1");
                    wallpaperField.setText(fullResUrl);
                });
                wpPresetsBox.getChildren().add(btn);
            } catch (Exception e) {}
        }
        
        // Avatar
        Label avatarLabel = new Label("Avatar URL (Flaticon image URL):");
        avatarLabel.getStyleClass().add("header-label");
        TextField avatarField = new TextField(currentAvatarUrl);
        avatarField.getStyleClass().add("chat-text-field");
        avatarField.textProperty().addListener((obs, oldVal, newVal) -> {
            currentAvatarUrl = newVal.trim();
            renderChat(currentActiveChat);
            if (userList != null) userList.refresh();
        });
        
        // Quick Flaticon presets
        HBox presetsBox = new HBox(10);
        String[] presets = {
            "https://cdn-icons-png.flaticon.com/512/4140/4140048.png", // boy
            "https://cdn-icons-png.flaticon.com/512/4140/4140047.png", // girl
            "https://cdn-icons-png.flaticon.com/512/4333/4333609.png", // hacker
            "https://cdn-icons-png.flaticon.com/512/2202/2202112.png"  // bot
        };
        for (String url : presets) {
            try {
                javafx.scene.image.ImageView img = new javafx.scene.image.ImageView(new javafx.scene.image.Image(url, 32, 32, true, true));
                Button btn = new Button();
                btn.setGraphic(img);
                btn.setStyle("-fx-background-color: transparent; -fx-cursor: hand;");
                btn.setOnAction(e -> {
                    avatarField.setText(url);
                });
                presetsBox.getChildren().add(btn);
            } catch (Exception e) {}
        }
        
        VBox themeSection = new VBox(8, themeLabel, themeBox);
        VBox wallpaperSection = new VBox(8, wallpaperLabel, wpPresetsBox, wallpaperField);
        VBox avatarSection = new VBox(8, avatarLabel, presetsBox, avatarField);
        
        root.getChildren().addAll(title, micSection, themeSection, wallpaperSection, avatarSection);
        
        ScrollPane scrollPane = new ScrollPane(root);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        
        Scene scene = new Scene(scrollPane, 420, 520); // Reduced width and height to remove empty whitespace
        try {
            scene.getStylesheets().add(getClass().getResource("/css/" + currentTheme).toExternalForm());
        } catch (Exception e) {}
        
        settingsStage.setScene(scene);
        settingsStage.show();
    }

    private void applyTheme() {
        if (primaryStage != null && primaryStage.getScene() != null) {
            primaryStage.getScene().getStylesheets().clear();
            try {
                primaryStage.getScene().getStylesheets().add(getClass().getResource("/css/" + currentTheme).toExternalForm());
            } catch (Exception e) {
                System.err.println("Could not load " + currentTheme);
            }
        }
    }

    private void applyWallpaper() {
        if (chatScrollPane != null) {
            if (currentWallpaper != null && !currentWallpaper.isEmpty()) {
                chatScrollPane.setStyle("-fx-background-image: url('" + currentWallpaper + "'); -fx-background-repeat: repeat; -fx-background-size: cover;");
            } else {
                chatScrollPane.setStyle("");
            }
        }
    }
}
