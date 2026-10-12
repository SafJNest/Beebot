// Adapted from R4J 2.8.1 (stelar7), Apache-2.0; SDK dependencies removed.
package com.R4B.lol;

import java.util.stream.Stream;

public enum MatchlistMatchType{
    RANKED("ranked"), NORMAL("normal"), TOURNAMENT("tourney"), TUTORIAL("tutorial");
    
    private final String apiname;
    
    MatchlistMatchType(final String apiName)
    {
        this.apiname = apiName;
    }
    
    public MatchlistMatchType getFromCode(final String type)
    {
        return Stream.of(MatchlistMatchType.values()).filter(t -> t.apiname.equalsIgnoreCase(type)).findFirst().orElse(null);
    }

    public String prettyName()
    {
        switch (this)
        {
            case RANKED -> { return "Ranked"; }
            case NORMAL -> { return "Normal"; }
            case TOURNAMENT -> { return "Tournament"; }
            case TUTORIAL -> { return "Tutorial"; }
            default -> { return "This enum does not have a pretty name"; }
        }
    }

    public String commonName()
    {
        return this.prettyName();
    }
    
    public String getValue()
    {
        return this.apiname;
    }

    public static MatchlistMatchType getFromOrdinal(int ordinal) {
        return ordinal < 0 || ordinal >= values().length ? null : values()[ordinal];
    }
}
