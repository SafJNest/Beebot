// Adapted from R4J 2.8.1 (stelar7), Apache-2.0; SDK dependencies removed.
package com.R4B.lol;

import java.util.*;
import java.util.stream.*;

public enum GameModeType{
    
    CLASSIC,
    
    ODIN,
    
    ARAM,
    
    TUTORIAL,
    TUTORIAL_MODULE_1,
    TUTORIAL_MODULE_2,
    TUTORIAL_MODULE_3,
    
    PRACTICETOOL,
    
    ONEFORALL,
    
    ASCENSION,
    
    FIRSTBLOOD,
    
    KINGPORO,
    
    SIEGE,
    
    ASSASSINATE,
    
    ARSR,
    
    DARKSTAR,
    
    STARGUARDIAN,
    
    URF,
    
    DOOMBOTSTEEMO,
    
    PROJECT,
    
    SNOWURF,
    
    GAMEMODEX,
    NEXUSBLITZ,
    
    ODYSSEY,
    
    ULTBOOK,
    UNKNOWN,

    CHERRY,

    BRAWL,

    TFT,

    STRAWBERRY,

    SWIFTPLAY,

    RUBY,
    RUBY_TRIAL_2,
    RUBY_TRIAL_3,

    KIWI,

    JADE,

    KIWI_JADE,
    ;

    public GameModeType getFromCode(final String gameMode)
    {
        if (gameMode.isEmpty()) {
            return (UNKNOWN);
        }
        
        return Stream.of(GameModeType.values()).filter(t -> t.name().equalsIgnoreCase(gameMode)).findFirst().orElse(null);
    }

    public String prettyName()
    {
        switch (this)
        {
            case CLASSIC -> { return "Classic"; }
            case ODIN -> { return "Dominion"; }
            case ARAM -> { return "ARAM"; }
            case TUTORIAL, TUTORIAL_MODULE_1, TUTORIAL_MODULE_2, TUTORIAL_MODULE_3 -> { return "Tutorial"; }
            case ONEFORALL -> { return "One for All"; }
            case ASCENSION -> { return "Ascension"; }
            case FIRSTBLOOD -> { return "Snowdown Showdown"; }
            case KINGPORO -> { return "King Poro"; }
            case SIEGE -> { return "Nexus Siege"; }
            case ASSASSINATE -> { return "Blood Hunt Assassin"; }
            case ARSR -> { return "All Random Summoners Rift"; }
            case DARKSTAR -> { return "Darkstar"; }
            case STARGUARDIAN -> { return "Invasion"; }
            case URF -> { return "URF"; }
            case DOOMBOTSTEEMO, RUBY_TRIAL_2, RUBY -> { return "Doombots"; }
            case PROJECT -> { return "Overcharge"; }
            case SNOWURF -> { return "Snow Battle ARURF"; }
            case GAMEMODEX -> { return "Nexus Blitz"; }
            case ODYSSEY -> { return "Odyssey"; }
            case SWIFTPLAY -> { return "Switfplay"; }
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

    public static GameModeType getFromOrdinal(int ordinal) {
        return ordinal < 0 || ordinal >= values().length ? null : values()[ordinal];
    }
}
