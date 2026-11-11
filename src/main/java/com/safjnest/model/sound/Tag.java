package com.safjnest.model.sound;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.NONE, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class Tag {

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

    @JsonProperty("id")
    @JsonSerialize(using = ToStringSerializer.class)
    public int getId() {
        return ID;
    }

    @JsonProperty("name")
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
