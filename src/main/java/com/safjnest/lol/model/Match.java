package com.safjnest.lol.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.TeamType;

public class Match {

    public int id;
    public String gameId;
    public LeagueShard leagueShard;
    public GameQueueType queue;
    public Map<TeamType, Integer> bans = new HashMap<>();
    public JSONObject events;
    public long timeStart;
    public long timeEnd;
    public String patch;
    public List<Participant> participants;

    /**
     * @return match duration in milliseconds
     */
    public long getDuration() {
        return timeEnd - timeStart;
    }
}
