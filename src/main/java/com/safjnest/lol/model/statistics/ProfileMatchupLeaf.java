package com.safjnest.lol.model.statistics;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.safjnest.lol.model.statistics.shared.ProfileLeafStats;

public class ProfileMatchupLeaf extends ProfileLeafStats {
    public Map<String, ProfileLeafStats> matchups = new LinkedHashMap<>();
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Map<String, ProfileLeafStats> synergies = new LinkedHashMap<>();
}
