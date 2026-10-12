// Adapted from R4J 2.8.1 (stelar7), Apache-2.0; SDK dependencies removed.
package com.R4B.lol;

import java.util.*;
import java.util.stream.*;

public enum WardType{
    
    BLUE_TRINKET,
    CONTROL_WARD,
    SIGHT_WARD,
    TEEMO_MUSHROOM,
    UNDEFINED,
    VISION_WARD,
    YELLOW_TRINKET,
    YELLOW_TRINKET_UPGRADE;

    public WardType getFromCode(final String type)
    {
        return Stream.of(WardType.values()).filter(t -> t.name().equalsIgnoreCase(type)).findFirst().orElse(null);
    }

    public String prettyName()
    {
        switch (this) {
            case BLUE_TRINKET -> { return "Blue Trinket"; }
            case CONTROL_WARD -> { return "Control Ward"; }
            case SIGHT_WARD -> { return "Sight Ward"; }
            case TEEMO_MUSHROOM -> { return "Teemo Mushroom"; }
            case UNDEFINED -> { return "Undefined"; }
            case VISION_WARD -> { return "Vision Ward"; }
            case YELLOW_TRINKET -> { return "Yellow Trinket"; }
            case YELLOW_TRINKET_UPGRADE -> { return "Yellow Trinket Upgrade"; }
            default -> { return "This enum does not have a pretty name"; }
        }
    }

    public String getCode()
    {
        return this.name();
    }

    public static WardType getFromOrdinal(int ordinal) {
        return ordinal < 0 || ordinal >= values().length ? null : values()[ordinal];
    }
}
