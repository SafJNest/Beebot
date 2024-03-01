package com.safjnest.Utilities.Bot.Guild.Alert;

public enum AlertType {
    BOOST("Boost Message"),
    LEAVE("Leave Message"),
    LEVEL_UP("Level Up Message"),
    WELCOME("Welcome Message");

    private final String description;

    AlertType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
