// Adapted from R4J 2.8.1 (stelar7), Apache-2.0; SDK dependencies removed.
package com.R4B.lol;

import java.util.*;
import java.util.stream.*;

public enum RoleType{

    DUO,
    
    NONE,
    
    SOLO,
    
    DUO_CARRY,
    
    DUO_SUPPORT,
    
    CARRY,
    
    SUPPORT,
    ;

    public RoleType getFromCode(final String code)
    {
        return Stream.of(RoleType.values()).filter(t -> t.name().equalsIgnoreCase(code)).findFirst().orElse(null);
    }

    public String prettyName()
    {
        switch (this)
        {
            case DUO -> { return "Duo"; }
            case NONE -> { return "None"; }
            case SOLO -> { return "Solo"; }
            case DUO_CARRY -> { return "Duo Carry"; }
            case DUO_SUPPORT -> { return "Duo Support"; }
            default -> { return "This enum does not have a pretty name"; }
        }
    }

    public String getValue()
    {
        return this.name();
    }

    public static RoleType getFromOrdinal(int ordinal) {
        return ordinal < 0 || ordinal >= values().length ? null : values()[ordinal];
    }
}
