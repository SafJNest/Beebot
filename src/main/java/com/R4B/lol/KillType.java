// Adapted from R4J 2.8.1 (stelar7), Apache-2.0; SDK dependencies removed.
package com.R4B.lol;

import java.util.stream.Stream;

public enum KillType{
    KILL_ACE,
    KILL_FIRST_BLOOD,
    KILL_MULTI,
    UNDEFINED,
    ;

    public KillType getFromCode(final String code)
    {
        return Stream.of(KillType.values()).filter(t -> t.name().equalsIgnoreCase(code)).findFirst().orElse(null);
    }

    public String prettyName()
    {
        return this.name();
    }

    public static KillType getFromOrdinal(int ordinal) {
        return ordinal < 0 || ordinal >= values().length ? null : values()[ordinal];
    }
}
