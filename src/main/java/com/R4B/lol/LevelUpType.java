// Adapted from R4J 2.8.1 (stelar7), Apache-2.0; SDK dependencies removed.
package com.R4B.lol;

import java.util.*;
import java.util.stream.*;

public enum LevelUpType{
    EVOLVE,
    NORMAL;

    public LevelUpType getFromCode(final String type)
    {
        return Stream.of(LevelUpType.values()).filter(t -> t.name().equalsIgnoreCase(type)).findFirst().orElse(null);
    }

    public String prettyName()
    {
        switch (this) {
            case EVOLVE -> { return "Evolve"; }
            case NORMAL -> { return "Normal"; }
            default -> { return "This enum does not have a pretty name"; }
        }
    }

    public String getCode()
    {
        return this.name();
    }

    public static LevelUpType getFromOrdinal(int ordinal) {
        return ordinal < 0 || ordinal >= values().length ? null : values()[ordinal];
    }
}
