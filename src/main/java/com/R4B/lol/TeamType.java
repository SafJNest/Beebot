// Adapted from R4J 2.8.1 (stelar7), Apache-2.0; SDK dependencies removed.
package com.R4B.lol;

import java.util.stream.Stream;

public enum TeamType{
    SUBTEAM(0),
    BLUE(100),
    RED(200),
    AI(300);
    
    private final Integer code;
    
    TeamType(final int code)
    {
        this.code = code;
    }
    
    public TeamType getFromCode(String value)
    {
        if (value.contains("."))
        {
            value = value.substring(0, value.indexOf("."));
        }
        
        return getFromId(Integer.parseInt(value));
    }
    
    public TeamType getFromId(final int teamId)
    {
        return Stream.of(TeamType.values()).filter(t -> t.code.equals(teamId)).findFirst().orElse(null);
    }
    
    public TeamType opposite()
    {
        switch (this)
        {
            case RED -> { return BLUE; }
            case BLUE -> { return RED; }
            case AI -> { return AI; }
            default -> { return null; }
        }
    }

    public String prettyName()
    {
        switch (this)
        {
            case BLUE -> { return "Blue"; }
            case RED -> { return "Red"; }
            case AI -> { return "AI"; }
            default -> { return "This enum does not have a pretty name"; }
        }
    }
    
    public Integer getValue()
    {
        return this.code;
    }

    public static TeamType getFromOrdinal(int ordinal) {
        return ordinal < 0 || ordinal >= values().length ? null : values()[ordinal];
    }
}
