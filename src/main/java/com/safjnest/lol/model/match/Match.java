package com.safjnest.lol.model.match;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.TeamType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierType;

public class Match {

    public int id;
    public String gameId;
    public LeagueShard leagueShard;
    public GameQueueType queue;
    public TierType rank;
    public long lastUpdate;
    public Map<TeamType, List<Integer>> bans = new HashMap<>();
    @JsonIgnore
    public JSONObject events;
    @JsonProperty("events")
    public Map<String, Object> eventData;
    public long timeStart;
    public long timeEnd;
    public String patch;
    public List<Participant> participants;

    @JsonIgnore
    public long getDuration() {
        return timeEnd - timeStart;
    }

    public void restoreEvents() {
        if (events == null) events = new JSONObject(eventData != null ? eventData : Map.of());
    }
}
