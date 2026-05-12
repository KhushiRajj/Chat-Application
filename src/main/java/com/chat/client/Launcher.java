package com.chat.client;

public class Launcher {
    public static void main(String[] args) {
        // This is a workaround required for JavaFX 11+ when running from a fat JAR.
        // It prevents Java from enforcing module path checks.
        ChatApplication.main(args);
    }
}
