package com.R4B.api.dto.statistics;

import java.util.LinkedHashMap;
import java.util.Map;

import com.R4B.api.dto.*;
import com.R4B.api.dto.statistics.shared.ProfileLeafStats;
import com.fasterxml.jackson.annotation.JsonInclude;

public class ProfileMatchupLeaf extends ProfileLeafStats {
    public Map<String, ProfileLeafStats> matchups = new LinkedHashMap<>();
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Map<String, ProfileLeafStats> synergies = new LinkedHashMap<>();
}
