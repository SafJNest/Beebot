package com.safjnest.model.guild.alert;

public enum AlertType {
    BOOST("Boost", "Boost Message"),
    LEAVE("Leave", "Leave Message"),
    LEVEL_UP("Level Up", "Level Up Message"),
    WELCOME("Welcome", "Welcome Message"),
    REWARD("Reward", "Reward"),
    TWITCH("Twitch", "Twitch");

    private final String description;
    private final String name;

    AlertType(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getName() {
        return this.name;
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
