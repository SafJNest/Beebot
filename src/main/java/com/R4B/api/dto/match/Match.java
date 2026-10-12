package com.R4B.api.dto.match;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.R4B.api.dto.*;
import com.R4B.lol.GameQueueType;
import com.R4B.lol.LeagueShard;
import com.R4B.lol.TeamType;
import com.R4B.lol.TierType;

public class Match {

    public int id;
    public String gameId;
    public LeagueShard leagueShard;
    public GameQueueType queue;
    public TierType rank;
    public long lastUpdate;
    public ResponseMetadata metadata;
    public Map<TeamType, List<Integer>> bans = new HashMap<>();
    @JsonProperty("events")
    public Map<String, Object> eventData;
    public long timeStart;
    public long timeEnd;
    public String patch;
    public List<Participant> participants;

    public static Match hydrated() {
        Match match = new Match();
        return match;
    }

    public Match setRank(TierType rank) {
        this.rank = rank;
        return this;
    }

    public Match setQueue(GameQueueType queue) {
        this.queue = queue;
        return this;
    }
    public Match setPatch(String patch) { this.patch = patch; return this; }

    public Match setTimeStart(long timeStart) {
        this.timeStart = timeStart;
        return this;
    }

    public Match setTimeEnd(long timeEnd) {
        this.timeEnd = timeEnd;
        return this;
    }

    @JsonIgnore
    public long getDuration() {
        return timeEnd - timeStart;
    }

    public Match withMetadata(ResponseMetadata value) {
        metadata = value;
        return this;
    }
}
