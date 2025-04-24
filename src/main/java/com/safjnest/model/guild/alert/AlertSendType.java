package com.safjnest.model.guild.alert;

public enum AlertSendType {
    CHANNEL("Channel"),
    PRIVATE("Private"),
    BOTH("Channel & Private");

    private final String name;

    AlertSendType(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public static AlertSendType getFromOrdinal(int ordinal) {
        for (AlertSendType type : values()) {
            if (type.ordinal() == ordinal) {
                return type;
            }
        }
        return null;
    }

    public static AlertSendType parse(String string) {
        switch (string.toLowerCase()) {
            case "channel":
                return AlertSendType.CHANNEL;
            case "private":
                return AlertSendType.PRIVATE;
            case "both":
                return AlertSendType.BOTH;
            default:
                throw new IllegalArgumentException("Invalid AlertSendType: " + string);
        }
    }
}
