// Adapted from R4J 2.8.1 (stelar7), Apache-2.0; SDK dependencies removed.
package com.R4B.lol;

import java.util.*;
import java.util.stream.*;

public enum TowerType{
    BASE_TURRET,
    FOUNTAIN_TURRET,
    INNER_TURRET,
    NEXUS_TURRET,
    OUTER_TURRET,
    UNDEFINED_TURRET;

    public TowerType getFromCode(final String type)
    {
        return Stream.of(TowerType.values()).filter(t -> t.name().equalsIgnoreCase(type)).findFirst().orElse(null);
    }

    public String prettyName()
    {
        switch (this)
        {
            case BASE_TURRET -> { return "Base Turret"; }
            case FOUNTAIN_TURRET -> { return "Fountain Turret"; }
            case INNER_TURRET -> { return "Inner Turret"; }
            case NEXUS_TURRET -> { return "Nexus Turret"; }
            case OUTER_TURRET -> { return "Outer Turret"; }
            case UNDEFINED_TURRET -> { return "Undefined Turret"; }
            default -> { return "This enum does not have a pretty name"; }
        }
    }

    public String getCode()
    {
        return this.name();
    }

    public static TowerType getFromOrdinal(int ordinal) {
        return ordinal < 0 || ordinal >= values().length ? null : values()[ordinal];
    }
}
