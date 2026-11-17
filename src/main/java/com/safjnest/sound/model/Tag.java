package com.safjnest.sound.model;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.NONE, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public final class Tag {

    public static final int MAX_TAG_SOUND = 5;

    private final int ID;
    private final String name;

    public Tag() {
        this(0, "");
    }

    public Tag(int ID, String name) {
        this.ID = ID;
        this.name = Objects.requireNonNull(name);
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
        return name.isEmpty();
    }

    public boolean isNew() {
        return ID == 0 && !isEmpty();
    }

    public boolean isStored() {
        return ID > 0 && !isEmpty();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other)
            return true;

        if (!(other instanceof Tag tag))
            return false;

        return ID == tag.ID && name.equals(tag.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ID, name);
    }

    @Override
    public String toString() {
        return "ID: " + ID + "\n" +
            "Name: " + name;
    }
    
}
