package com.R4B.api.dto.statistics;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.R4B.api.dto.*;
import com.R4B.api.dto.statistics.shared.ProfileLeafStats;
import com.R4B.lol.CanonicalQueue;

public class ProfileStatistics {
    public long timeStart;
    public long timeEnd;
    public long lastUpdate;
    public long oldestMatchAt;
    public long newestMatchAt;
    public Map<Integer, Map<CanonicalQueue, Map<String, ProfileLeafStats>>> champions = new LinkedHashMap<>();
    public Map<String, Long> pings = new LinkedHashMap<>();
    public Map<Integer, Long> spellOne = new LinkedHashMap<>();
    public Map<Integer, Long> spellTwo = new LinkedHashMap<>();
    @JsonIgnore public ProfileLeafStats total = new ProfileLeafStats();
    @JsonIgnore public java.util.List<ProfileLeafStats> queueStats = new java.util.ArrayList<>();
    @JsonIgnore public java.util.List<ProfileLeafStats> laneStats = new java.util.ArrayList<>();
    @JsonIgnore public java.util.List<ProfileLeafStats> championStats = new java.util.ArrayList<>();

    public ProfileStatistics() {}

    public ProfileStatistics(long timeStart) {
        this.timeStart = timeStart;
        timeEnd = timeStart;
    }
}
