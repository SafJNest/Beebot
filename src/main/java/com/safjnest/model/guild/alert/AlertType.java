package com.safjnest.model.guild.alert;

public enum AlertType {
    BOOST("Boost Message"),
    LEAVE("Leave Message"),
    LEVEL_UP("Level Up Message"),
    WELCOME("Welcome Message"),
    REWARD("Reward"),
    TWITCH("Twitch");

    private final String description;

    AlertType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public static AlertType getFromOrdinal(int ordinal) {
        for (AlertType type : values()) {
            if (type.ordinal() == ordinal) {
                return type;
            }
        }
        return null;
    }

    public static AlertType getAlertType(String type) {
        switch (type) {
            case "boost":
                return AlertType.BOOST;
            case "leave":
                return AlertType.LEAVE;
            case "levelUp":
                return AlertType.LEVEL_UP;
            case "welcome":
                return AlertType.WELCOME;
            case "reward":
                return AlertType.REWARD;
            case "twitch":
                return AlertType.TWITCH;
            default:
                return null;
        }
    }
}
