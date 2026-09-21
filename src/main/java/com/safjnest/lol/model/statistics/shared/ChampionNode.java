package com.safjnest.lol.model.statistics.shared;

import java.util.LinkedHashMap;
import java.util.Map;

public class ChampionNode {

    public long bans;

    public Map<String, ChampionLeafStats> lanes = new LinkedHashMap<>();

    public ChampionNode() {}

    public ChampionNode(long bans) {
        this.bans = bans;
    }

    public ChampionLeafStats overall() {
        ChampionLeafStats result = new ChampionLeafStats();
        for (ChampionLeafStats leaf : lanes.values()) result.merge(leaf);
        return result;
    }

    public ChampionLeafStats lane(String lane) {
        return lanes.get(lane);
    }
}
