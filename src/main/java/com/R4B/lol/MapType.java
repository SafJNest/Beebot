// Adapted from R4J 2.8.1 (stelar7), Apache-2.0; SDK dependencies removed.
package com.R4B.lol;

import java.util.stream.Stream;

public enum MapType{
    UNKNOWN(0, new Rectangle(0, 0, 0, 0)),

    SUMMONERS_RIFT_SUMMER_OLD(1, new Rectangle(-269, -83, 14276, 14522)),

    SUMMONERS_RIFT_AUTUMN_OLD(2, new Rectangle(-269, -83, 14276, 14522)),
    
    PROVING_GROUNDS(3, new Rectangle(-500, -500, 15000, 15000)),
    
    TWISTED_TREELINE_OLD(4, new Rectangle(0, 0, 15398, 15398)),
    
    CRYSTAL_SCAR(8, new Rectangle(0, 0, 13987, 13987)),
    
    TWISTED_TREELINE(10, new Rectangle(0, 0, 15398, 15398)),

    SUMMONERS_RIFT(11, new Rectangle(-269, -83, 14276, 14522)),
    
    HOWLING_ABYSS(12, new Rectangle(-28, -19, 12849, 12858)),
    
    BUTCHERS_BRIDGE(14, new Rectangle(-28, -19, 12849, 12858)),

    COSMIC_RUINS(16, new Rectangle(20, 20, 6970, 6970)),

    VALORAN_CITY_PARK(18, new Rectangle(0, 0, 28767, 28767)),
    
    SUBSTRUCTURE_43(19, new Rectangle(0, 0, 12056, 12056)),
    
    CRASH_SITE(20, new Rectangle(0, 0, 12056, 12056)),
    
    NEXUS_BLITZ(21, new Rectangle(0, 0, 12056, 12056)),
    
    TEAMFIGHT_TACTICS(22, new Rectangle(0, 0, 12056, 12056)),

    ARENA(30, new Rectangle(0, 0, 12056, 12056)),

    STRAWBERRY(33, new Rectangle(0, 0, 12056, 12056)),

	BRAWL(35, new Rectangle(0, 0, 12056, 12056)),

	SUMMONERS_RIFT_CLASSIC_VERSION(453, new Rectangle(0, 0, 16000, 16000)),
	
	;

    private final Integer   mapId;
    private final Rectangle bounds;

    MapType(final Integer code, final Rectangle bounds)
    {
        this.mapId = code;
        this.bounds = bounds;
    }

    public MapType getFromCode(String value)
    {
        if (value.contains("."))
        {
            value = value.substring(0, value.indexOf("."));
        }
        
        return getFromId(Integer.parseInt(value));
    }
    
    public MapType getFromId(final int mapId)
    {
        return Stream.of(MapType.values()).filter(t -> t.mapId.equals(mapId)).findFirst().orElse(null);
    }

    public String prettyName()
    {
        switch (this)
        {
            case SUMMONERS_RIFT_SUMMER_OLD -> { return "Summoners Rift Old Skin"; }
            case SUMMONERS_RIFT_AUTUMN_OLD -> { return "Summoners Rift Autumn Skin"; }
            case PROVING_GROUNDS -> { return "Proving Grounds"; }
            case TWISTED_TREELINE_OLD -> { return "Twisted Treeline Old Skin"; }
            case CRYSTAL_SCAR -> { return "Crystal Scar"; }
            case TWISTED_TREELINE -> { return "Twisted Treeline"; }
            case SUMMONERS_RIFT -> { return "Summoners Rift"; }
            case HOWLING_ABYSS -> { return "Howling Abyss"; }
            case BUTCHERS_BRIDGE -> { return "Butchers Bridge"; }
            case COSMIC_RUINS -> { return "Cosmic Ruins"; }
            case VALORAN_CITY_PARK -> { return "Valoran City Park"; }
            case SUBSTRUCTURE_43 -> { return "Substructure 43"; }
            case CRASH_SITE -> { return "Crash Site"; }
            case NEXUS_BLITZ -> { return "Nexus Blitz"; }
            case TEAMFIGHT_TACTICS -> { return "Teamfight Tactics"; }
            default -> { return "This enum does not have a pretty name"; }
        }
    }

    public Rectangle getBounds()
    {
        return this.bounds;
    }

    public Integer getId()
    {
        return this.mapId;
    }

    public int getValue()
    {
        return getId();
    }

    public static MapType getFromOrdinal(int ordinal) {
        return ordinal < 0 || ordinal >= values().length ? null : values()[ordinal];
    }
}
