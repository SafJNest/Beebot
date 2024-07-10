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
}
