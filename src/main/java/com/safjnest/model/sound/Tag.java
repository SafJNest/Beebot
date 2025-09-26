package com.safjnest.model.sound;

import java.io.Serializable;

public class Tag implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final int MAX_TAG_SOUND = 5;

    private final int ID;
    private String name;

    public Tag() {
        this.ID = 0;
        this.name = "";
    }

    public Tag(int ID, String name) {
        this.ID = ID;
        this.name = name;
    }

    public int getId() {
        return ID;
    }

    public String getName() {
        return name;
    }

    public boolean isEmpty() {
        return ID == 0 || name.isEmpty();
    }

    @Override
    public String toString() {
        return "ID: " + ID + "\n" +
            "Name: " + name;
    }
    
}
