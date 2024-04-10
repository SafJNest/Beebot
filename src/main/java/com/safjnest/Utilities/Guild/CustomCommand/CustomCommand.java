package com.safjnest.Utilities.Guild.CustomCommand;

public class CustomCommand {
    private final int ID;
    private String name;
    private String description;
    private boolean isSlash;



    public CustomCommand(int ID, String name, String description, boolean isSlash) {
        this.ID = ID;
        this.name = name;
        this.description = description;
        this.isSlash = isSlash;
    }
}
