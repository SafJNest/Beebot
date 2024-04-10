package com.safjnest.Utilities.Guild.CustomCommand;

public class Task {
    private final int ID;
    private TaskType type;
    private String[] values;

    public Task(int ID, TaskType type, String[] values) {
        this.ID = ID;
        this.type = type;
        this.values = values;
    }
}
