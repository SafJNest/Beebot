// Adapted from R4J 2.8.1 (stelar7), Apache-2.0; SDK dependencies removed.
package com.R4B.lol;

import java.util.*;
import java.util.stream.*;

public enum MonsterType{
    ATAKHAN,
    BARON_NASHOR,
    BLUE_GOLEM,
    DRAGON,
    HORDE,
    RED_LIZARD,
    RIFTHERALD,
    VILEMAW,
    ;

    public MonsterType getFromCode(final String type)
    {
        return Stream.of(MonsterType.values()).filter(t -> t.name().equalsIgnoreCase(type)).findFirst().orElse(null);
    }

    public String prettyName()
    {
        switch (this)
        {
            case BARON_NASHOR -> { return "Baron Nashor"; }
            case BLUE_GOLEM -> { return "Blue Golem"; }
            case DRAGON -> { return "Dragon"; }
            case RED_LIZARD -> { return "Red Lizard"; }
            case RIFTHERALD -> { return "Rift Herald"; }
            case VILEMAW -> { return "Vilemaw"; }
            case HORDE -> { return "Voidgrub"; }
            case ATAKHAN -> { return "Atakhan"; }
            default -> { return "This enum does not have a pretty name"; }
        }
    }

    public String getCode()
    {
        return this.name();
    }

    public static MonsterType getFromOrdinal(int ordinal) {
        return ordinal < 0 || ordinal >= values().length ? null : values()[ordinal];
    }
}
