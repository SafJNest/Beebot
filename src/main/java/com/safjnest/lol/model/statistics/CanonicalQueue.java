package com.safjnest.lol.model.statistics;

import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;

public enum CanonicalQueue {
    RANKED_SOLO,
    RANKED_FLEX,
    NORMAL_DRAFT,
    NORMAL_BLIND,
    ARAM,
    ARENA,
    SWIFTPLAY,
    URF,
    ULTBOOK,
    NEXUS_BLITZ,
    SWARM,
    SPECIAL,
    OTHER;

    public static CanonicalQueue from(GameQueueType queue) {
        if (queue == null) return OTHER;
        return switch (queue) {
            case RANKED_SOLO_5X5, TEAM_BUILDER_RANKED_SOLO, JADE_RANKED_SOLO_5X5 -> RANKED_SOLO;
            case RANKED_FLEX_SR, TEAM_BUILDER_DRAFT_RANKED_5X5 -> RANKED_FLEX;
            case NORMAL_5X5_DRAFT, TEAM_BUILDER_DRAFT_UNRANKED_5X5 -> NORMAL_DRAFT;
            case NORMAL_5V5_BLIND_PICK, NORMAL_5X5_BLIND_PICK_OLD -> NORMAL_BLIND;
            case ARAM, ARAM_5X5, ARAM_5X5_OLD, ARAM_CLASH, ARAM_BOTS -> ARAM;
            case CHERRY -> ARENA;
            case SWIFTPLAY, QUICKPLAY_NORMAL -> SWIFTPLAY;
            case URF, URF_5X5, ALL_RANDOM_URF, SNOW_BATTLE_ARURF -> URF;
            case ULTBOOK -> ULTBOOK;
            case NEXUS_BLITZ -> NEXUS_BLITZ;
            case STRAWBERRY -> SWARM;
            case ONEFORALL_5X5, DOOMBOTS_V2, BRAWL, KIWI, JADE -> SPECIAL;
            default -> OTHER;
        };
    }

    public boolean arena() {
        return this == ARENA;
    }
}
