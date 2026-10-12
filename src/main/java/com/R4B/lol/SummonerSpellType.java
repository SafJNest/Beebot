// Adapted from R4J 2.8.1 (stelar7), Apache-2.0; SDK dependencies removed.
package com.R4B.lol;

import java.util.*;
import java.util.stream.Stream;

public enum SummonerSpellType{
    
    UNKNOWN("UNKNOWN", 0),
    
    CLEANSE("SummonerBoost", 1, 71),
    
    TELEPORT("SummonerTeleport", 12, 712),
    
    TO_THE_KING("SummonerPoroRecall", 30),
    
    IGNITE("SummonerDot", 14, 714),
    
    GHOST("SummonerHaste", 6, 76),
    
    MARK("SummonerSnowball", 32),
    
    HEAL("SummonerHeal", 7, 77),
    
    SMITE("SummonerSmite", 11, 711),
    
    EXHAUST("SummonerExhaust", 3, 73),
    
    PORO_TOSS("SummonerPoroThrow", 31),
    
    CLARITY("SummonerMana", 13, 713),
    
    CLAIRVOYANCE("SummonerClairvoyance", 2, 75),
    
    BARRIER("SummonerBarrier", 21, 721),
    
    FLASH("SummonerFlash", 4, 2202, 2203, 74),
    
    GARRISON("SummonerOdinGarrison", 17),
    
    REVIVE("SummonerRevive", 10, 777),
    
    NEXUS_SIEGE_1("SiegeEmptySlot", 33),
    
    NEXUS_SIEGE_2("SiegeEmptySlot", 34),
    
    DARK_STAR_1("Summoner_Empty", 35),
    
    DARK_STAR_2("Summoner_Empty", 36),
    
    SNOW_MARK("SummonerMark", 39),
    
    BACKTRACK("Summoner_Backtrack", 5),
    
    RESUSCITATE("SummonerTemp2", 50),
    
    GHOST_2("Summoner_haste", 51),
    
    WARP("SummonerTemp1", 52),
    
    ULTBOOK_1("Summoner_Empty", 54),
    
    AUTO_SMITE("Summoner_EmptySmite", 55),
    
    FLEE("Summoner_Flee", 2201),

    FORTIFY("SummonerFortify", 705),
    
    RALLY("SummonerRally", 709),
    
    SURGE("SummonerBattleCry", 716),
    
    PROMOTE("SummonerPromote", 720),

    ;

    private final Integer[] ids;
    private final String  apiName;

    SummonerSpellType(final String name, final Integer... ids)
    {
        this.ids = ids;
        this.apiName = name;
    }

    public SummonerSpellType getFromCode(final String type)
    {
        SummonerSpellType byName = Stream.of(SummonerSpellType.values()).filter(t -> t.getApiName().equals(type)).findFirst().orElse(null);
        if (byName != null)
        {
            return byName;
        }
        
        return Stream.of(SummonerSpellType.values()).filter(t -> Arrays.asList(t.ids).contains(Integer.parseInt(type))).findFirst().orElse(null);
    }

    public String prettyName()
    {
        switch (this) {
            case UNKNOWN -> { return "Unknown"; }
            case CLEANSE -> { return "Cleanse"; }
            case TELEPORT -> { return "Teleport"; }
            case TO_THE_KING -> { return "To The King"; }
            case IGNITE -> { return "Ignite"; }
            case GHOST, GHOST_2 -> { return "Ghost"; }
            case MARK -> { return "Mark"; }
            case HEAL -> { return "Heal"; }
            case SMITE -> { return "Smite"; }
            case EXHAUST -> { return "Exhaust"; }
            case PORO_TOSS -> { return "Poro Toss"; }
            case CLARITY -> { return "Clarity"; }
            case CLAIRVOYANCE -> { return "Clairvoyance"; }
            case BARRIER -> { return "Barrier"; }
            case FLASH -> { return "Flash"; }
            case GARRISON -> { return "Garrison"; }
            case REVIVE -> { return "Revive"; }
            case NEXUS_SIEGE_1 -> { return "Siege Slot 1"; }
            case NEXUS_SIEGE_2 -> { return "Siege Slot 2"; }
            case DARK_STAR_1 -> { return "Disabled"; }
            case DARK_STAR_2 -> { return "Disabled"; }
            case SNOW_MARK -> { return "Snow Mark"; }
            case BACKTRACK -> { return "Backtrack"; }
            case RESUSCITATE -> { return "Resuscitate"; }
            case WARP -> { return "Warp"; }
            case FORTIFY -> { return "Fortify"; }
            case RALLY -> { return "Rally"; }
            case SURGE -> { return "Surge"; }
            case PROMOTE -> { return "Promote"; }
            default -> { return "This enum does not have a pretty name"; }
        }
    }

    public Integer getValue()
    {
        return this.ids[0];
    }

    public String getApiName()
    {
        return apiName;
    }

    public static SummonerSpellType getFromOrdinal(int ordinal) {
        return ordinal < 0 || ordinal >= values().length ? null : values()[ordinal];
    }
}
