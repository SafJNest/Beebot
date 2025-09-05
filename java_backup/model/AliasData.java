package com.safjnest.model;

public class AliasData {
    private final int ID;
    private String name;
    private String command;

    public AliasData(int ID, String name, String command) {
        this.ID = ID;
        this.name = name;
        this.command = command;
    }

    public int getID() {
        return ID;
    }

    public String getName() {
        return name;
    }

    public String get() {
        return command;
    }

    public String getArgs() {
        return command.substring(command.indexOf(" ") + 1);
    }

    public String getCommand() {
        return command;
    }
    

    public String getBaseCommand() {
        return command.split(" ")[0];
    }

    @Override
    public String toString() {
        return "Alias [ID=" + ID + ", name=" + name + ", command=" + command + "]";
    }

}
