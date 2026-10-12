// Adapted from R4J 2.8.1 (stelar7), Apache-2.0; SDK dependencies removed.
package com.R4B.lol;

import java.util.stream.Stream;

public enum RegionShard{
    
    UNKNOWN("", ""),
    
    AMERICAS("AMERICAS", "americas"),
    
    EUROPE("EUROPE", "europe"),
    
    ASIA("ASIA", "asia"),
    
    GARENA("GARENA", "garena"),
    
    PBE("PBE", "pbe"),
    
    ESPORTS("ESPORTS", "esports"),
    
    SEA("SEA", "sea"),
    ;
    
    private String[] key;
    
    RegionShard(String... s)
    {
        this.key = s;
    }

    public static RegionShard getFromCode(final String code)
    {
        return Stream.of(RegionShard.values()).filter(t -> t.key[0].equalsIgnoreCase(code)).findFirst().orElse(null);
    }

    public String getValue()
    {
        return this.key[0];
    }

    public String getRealmValue()
    {
        return this.key[1];
    }

    public String toString()
    {
        return this.getValue();
    }

    public static RegionShard getFromOrdinal(int ordinal) {
        return ordinal < 0 || ordinal >= values().length ? null : values()[ordinal];
    }
}
