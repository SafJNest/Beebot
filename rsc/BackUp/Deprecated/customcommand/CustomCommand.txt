package com.safjnest.model.guild.customcommand;

import java.util.List;

public class CustomCommand {
    private final int ID;
    private String name;
    private String description;
    private boolean isSlash;

    private List<Option> options;
    private List<Task> tasks;



    public CustomCommand(int ID, String name, String description, boolean isSlash) {
        this.ID = ID;
        this.name = name;
        this.description = description;
        this.isSlash = isSlash;
        options = new java.util.ArrayList<>();
        tasks = new java.util.ArrayList<>();
    }

    public void addTask(Task task) {
        tasks.add(task);
    }

    public void addOption(Option option) {
        options.add(option);
    }

    public int getID() {
        return ID;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean isSlash() {
        return isSlash;
    }

    public List<Option> getOptions() {
        return options;
    }

    public List<Task> getTasks() {
        return tasks;
    }

    @Override
    public String toString() {
        return "CustomCommand{" +
                "ID=" + ID +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", isSlash=" + isSlash +
                ", options=" + options +
                ", tasks=" + tasks +
                '}';
    }
}
