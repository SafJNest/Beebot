package com.safjnest.Utilities.Guild.CustomCommand;

public enum TaskType {
    SEND_MESSAGE(0),
    DELETE_CHANNEL(1);

    private int value;

    private TaskType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    
    public static TaskType fromValue(int value) {
        for (TaskType type : TaskType.values()) {
            if (type.getValue() == value) {
                return type;
            }
        }
        return null;
    }

}
