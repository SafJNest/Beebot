package com.safjnest.lol.model.statistics;

import java.util.LinkedHashMap;
import java.util.Map;

import com.safjnest.lol.model.statistics.shared.ProfileLeafStats;

public class ProfileMatchupLeaf extends ProfileLeafStats {
    public Map<Integer, ProfileLeafStats> matchups = new LinkedHashMap<>();
}
