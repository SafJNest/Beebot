// Adapted from R4J 2.8.1 (stelar7), Apache-2.0; SDK dependencies removed.
package com.R4B.lol;

import java.util.stream.Stream;

public enum TransformType{
    ASSASSIN,
    SLAYER,
    ;

    public TransformType getFromCode(String type)
    {
        return Stream.of(TransformType.values()).filter(t -> t.name().equals(type)).findFirst().orElse(null);
    }

    public String prettyName()
    {
        return this.name();
    }

    public static TransformType getFromOrdinal(int ordinal) {
        return ordinal < 0 || ordinal >= values().length ? null : values()[ordinal];
    }
}
