// Adapted from R4J 2.8.1 (stelar7), Apache-2.0; SDK dependencies removed.
package com.R4B.lol;

import java.util.*;
import java.util.stream.Stream;

public enum GameType{
    
    CUSTOM_GAME("CUSTOM_GAME", "CUSTOM"),
    
    TUTORIAL_GAME("TUTORIAL_GAME", "TUTORIAL"),
    
    MATCHED_GAME("MATCHED_GAME", "MATCHED"),
    
    UNKNOWN("UNKNOWN");
    
    private final List<String> codes;
    
    GameType(String... codes)
    {
        this.codes = Arrays.asList(codes);
    }

    public GameType getFromCode(final String gameType)
    {
        if (gameType.isEmpty())
        {
            return (UNKNOWN);
        }
        
        return Stream.of(GameType.values()).filter(t -> t.codes.contains(gameType.toUpperCase())).findFirst().orElse(null);
    }

    public String prettyName()
    {
        switch (this)
        {
            case CUSTOM_GAME -> { return "Custom Game"; }
            case TUTORIAL_GAME -> { return "Tutorial Game"; }
            case MATCHED_GAME -> { return "Matched Game"; }
            default -> { return "This enum does not have a pretty name"; }
        }
    }

    public String getValue()
    {
        return this.name();
    }

    public String getCode()
    {
        return this.name();
    }

    public static GameType getFromOrdinal(int ordinal) {
        return ordinal < 0 || ordinal >= values().length ? null : values()[ordinal];
    }
}
