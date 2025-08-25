package com.safjnest.util.lol.model;

import java.util.HashMap;
import java.util.List;

import org.json.JSONObject;

import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.TeamType;

public class MatchData {
    public int id;
    public String gameId;
    public int leagueShard;
    public GameQueueType gameType;
    public HashMap<TeamType, Integer> bans;      
    public JSONObject events;
    public long timeStart;
    public long timeEnd;
    public String patch;
    public List<ParticipantData> participants;
}