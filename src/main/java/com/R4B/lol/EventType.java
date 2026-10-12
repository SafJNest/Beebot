// Adapted from R4J 2.8.1 (stelar7), Apache-2.0; SDK dependencies removed.
package com.R4B.lol;

import java.util.*;
import java.util.stream.*;

public enum EventType{
    ASCENDED_EVENT,
    BUILDING_KILL,
    CAPTURE_POINT,
    CHAMPION_KILL,
    ELITE_MONSTER_KILL,
    ITEM_DESTROYED,
    ITEM_PURCHASED,
    ITEM_SOLD,
    ITEM_UNDO,
    PORO_KING_SUMMON,
    SKILL_LEVEL_UP,
    WARD_KILL,
    WARD_PLACED,
    TURRET_PLATE_DESTROYED,
    PAUSE_START,
    PAUSE_END,
    LEVEL_UP,
    CHAMPION_SPECIAL_KILL,
    GAME_END,
    DRAGON_SOUL_GIVEN,
    CHAMPION_TRANSFORM,
    OBJECTIVE_BOUNTY_PRESTART,
    OBJECTIVE_BOUNTY_FINISH,
    FEAT_UPDATE,
    ;

    public EventType getFromCode(final String type)
    {
        return Stream.of(EventType.values()).filter(t -> t.name().equalsIgnoreCase(type)).findFirst().orElse(null);
    }

    public String prettyName()
    {
        switch (this)
        {
            
            case ASCENDED_EVENT -> { return "Ascended"; }
            case BUILDING_KILL -> { return "Building Killed"; }
            case CAPTURE_POINT -> { return "Point Captured"; }
            case CHAMPION_KILL -> { return "Champion Killed"; }
            case ELITE_MONSTER_KILL -> { return "Elite Monster Killed"; }
            case ITEM_DESTROYED -> { return "Item Destroyed"; }
            case ITEM_PURCHASED -> { return "item Purchased"; }
            case ITEM_SOLD -> { return "Item Sold"; }
            case ITEM_UNDO -> { return "Item Undone"; }
            case PORO_KING_SUMMON -> { return "Poro King Summoned"; }
            case SKILL_LEVEL_UP -> { return "Skill Leveled"; }
            case WARD_KILL -> { return "Ward Killed"; }
            case WARD_PLACED -> { return "Ward Placed"; }
            default -> { return "This enum does not have a pretty name"; }
        }
    }

    public String getCode()
    {
        return this.name();
    }

    public static EventType getFromOrdinal(int ordinal) {
        return ordinal < 0 || ordinal >= values().length ? null : values()[ordinal];
    }
}
