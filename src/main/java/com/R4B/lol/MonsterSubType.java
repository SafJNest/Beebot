// Adapted from R4J 2.8.1 (stelar7), Apache-2.0; SDK dependencies removed.
package com.R4B.lol;

import java.util.stream.Stream;

public enum MonsterSubType{
    UNKNOWN, EARTH_DRAGON, FIRE_DRAGON, WATER_DRAGON, AIR_DRAGON, ELDER_DRAGON, CHEMTECH_DRAGON, HEXTECH_DRAGON;
    
    public MonsterSubType getFromCode(String id)
    {
        return Stream.of(MonsterSubType.values()).filter(t -> t.name().equalsIgnoreCase(id)).findFirst().orElse(null);
    }

    public String prettyName()
    {
        switch (this)
        {
            case EARTH_DRAGON -> { return "Earth Dragon"; }
            case FIRE_DRAGON -> { return "Fire Dragon"; }
            case WATER_DRAGON -> { return "Water Dragon"; }
            case AIR_DRAGON -> { return "Air Dragon"; }
            case ELDER_DRAGON -> { return "Elder Dragon"; }
            default -> { return "This enum does not have a pretty name"; }
        }
    }

    public static MonsterSubType getFromOrdinal(int ordinal) {
        return ordinal < 0 || ordinal >= values().length ? null : values()[ordinal];
    }
}
