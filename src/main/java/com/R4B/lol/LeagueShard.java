// Adapted from R4J 2.8.1 (stelar7), Apache-2.0; SDK dependencies removed.
package com.R4B.lol;

import java.util.*;
import java.util.stream.Stream;

import com.R4B.lol.*;

public enum LeagueShard{
    
    UNKNOWN("", "", "unknown"),
    
    BR1("BR1", "br"),
    
    EUN1("EUN1", "eune"),
    
    EUW1("EUW1", "euw"),
    
    JP1("JP1", "jp"),
    
    KR("KR", "kr"),
    
    LA1("LA1", "lan", "WAS_LA1"),
    
    LA2("LA2", "las"),
    
    NA1("NA1", "na"),
    
    OC1("OC1", "oc"),
    
    TR1("TR1", "tr"),
    
    RU("RU", "ru"),
    
    PBE1("PBE1", "pbe"),
    
    SG2("SG2", "sg"),
    
    PH2("PH2", "ph"),
    
    ID1("ID1", "id"),
    
    VN2("VN2", "vn"),
    
    TH2("TH2", "th"),
    
    TW2("TW2", "tw"),
    
    ME1("ME1", "me");

    private final String[] keys;
    
    LeagueShard(String... s)
    {
        this.keys = s;
    }

    public LeagueShard getFromCode(final String code)
    {
        return fromString(code);
    }

    public String prettyName()
    {
        switch (this)
        {
            case UNKNOWN -> { return "Unknown"; }
            case BR1 -> { return "Brazil"; }
            case EUN1 -> { return "Europe Nordic & East"; }
            case EUW1 -> { return "Europe West"; }
            case JP1 -> { return "Japan"; }
            case KR -> { return "Korea"; }
            case LA1 -> { return "Latin America North"; }
            case LA2 -> { return "Latin America South"; }
            case NA1 -> { return "North America"; }
            case OC1 -> { return "Oceania"; }
            case TR1 -> { return "Turkey"; }
            case RU -> { return "Russia"; }
            case PBE1 -> { return "Public Beta Environment"; }
            case SG2 -> { return "Singapore"; }
            case PH2 -> { return "Philippines"; }
            case ID1 -> { return "Indonesia"; }
            case VN2 -> { return "Vietnam"; }
            case TH2 -> { return "Thailand"; }
            case TW2 -> { return "Taiwan"; }
            case ME1 -> { return "Middle East"; }
            default -> { return "This enum does not have a pretty name"; }
        }
    }
    
    public static LeagueShard fromString(final String code)
    {
        return Stream.of(LeagueShard.values()).filter(t -> Stream.of(t.keys).anyMatch(s -> s.equalsIgnoreCase(code))).findFirst().orElse(null);
    }

    public String getValue()
    {
        return this.keys[0];
    }

    public String getRealmValue()
    {
        return this.keys[1];
    }
    
    public RegionShard toRegionShard()
    {
        switch (this)
        {
            
            case UNKNOWN -> { return RegionShard.UNKNOWN; }
            case BR1, NA1, LA1, LA2 -> { return RegionShard.AMERICAS; }
            case EUN1, EUW1, TR1, RU, ME1 -> { return RegionShard.EUROPE; }
            case JP1, KR -> { return RegionShard.ASIA; }
            case ID1, SG2, PH2, VN2, TH2, TW2, OC1 -> { return RegionShard.SEA; }
            case PBE1 -> { return RegionShard.PBE; }
            default -> { throw new IllegalArgumentException("Unsupported enum value: " + this); }
        }
    }

    public RegionShard toAccountRegionShard()
    {
        switch (this)
        {
            
            case UNKNOWN -> { return RegionShard.UNKNOWN; }
            case BR1, NA1, LA1, LA2 -> { return RegionShard.AMERICAS; }
            case EUN1, EUW1, TR1, RU, ME1 -> { return RegionShard.EUROPE; }
            case JP1, KR, ID1, SG2, PH2, VN2, TH2, TW2, OC1 -> { return RegionShard.ASIA; }
            case PBE1 -> { return RegionShard.PBE; }
            default -> { throw new IllegalArgumentException("Unsupported enum value: " + this); }
        }
    }

    public String toString()
    {
        return this.getValue();
    }
    
    public static List<LeagueShard> getDefaultPlatforms()
    {
        return Arrays.asList(RU, KR, BR1, OC1, JP1, NA1, EUN1, EUW1, TR1, LA1, LA2);
    }
    
    public static List<LeagueShard> getSpectatorPlatforms()
    {
        return Arrays.asList(RU, KR, BR1, OC1, JP1, NA1, EUN1, EUW1, ME1);
    }
    
    public static List<LeagueShard> getGarenaPlatforms()
    {
        return Arrays.asList(ID1);
    }
    
    public static List<LeagueShard> getValidPlatforms()
    {
        return Arrays.asList(RU, KR, BR1, OC1, JP1, NA1, EUN1, EUW1, TR1, LA1, LA2, SG2, PH2, ID1, VN2, TH2, TW2, ME1);
    }
    
    public String[] getKeys()
    {
        return this.keys;
    }

    public static LeagueShard getFromOrdinal(int ordinal) {
        return ordinal < 0 || ordinal >= values().length ? null : values()[ordinal];
    }

    public static String getRegionCode(LeagueShard shard) {
        return switch (shard) {
            case NA1, JP1, BR1, TR1, SG2, PH2, TW2, VN2, TH2 -> shard.getValue();
            case KR, RU -> shard.getRealmValue() + "1";
            default -> shard.getRealmValue();
        };
    }

    public static RegionShard getAccountRegion(LeagueShard shard) {
        return switch (shard) {
            case VN2, OC1, SG2, PH2, TH2, TW2 -> RegionShard.ASIA;
            default -> shard.toRegionShard();
        };
    }

    public static String cacheRegion(LeagueShard shard) {
        return shard.toRegionShard().name();
    }}
