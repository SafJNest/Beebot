// Adapted from R4J 2.8.1 (stelar7), Apache-2.0; SDK dependencies removed.
package com.R4B.lol;

import java.util.*;
import java.util.stream.*;

public enum BuildingType{
    INHIBITOR_BUILDING,
    TOWER_BUILDING;

    public BuildingType getFromCode(final String type)
    {
        return Stream.of(BuildingType.values()).filter(t -> t.name().equalsIgnoreCase(type)).findFirst().orElse(null);
    }

    public String getCode()
    {
        return this.name();
    }

    public String prettyName()
    {
        switch (this)
        {
            case INHIBITOR_BUILDING -> { return "Inhibitor"; }
            case TOWER_BUILDING -> { return "Tower"; }
            default -> { return "This enum does not have a pretty name"; }
        }
    }

    public static BuildingType getFromOrdinal(int ordinal) {
        return ordinal < 0 || ordinal >= values().length ? null : values()[ordinal];
    }
}
