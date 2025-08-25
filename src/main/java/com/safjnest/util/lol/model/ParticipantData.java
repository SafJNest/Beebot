package com.safjnest.util.lol.model;

import java.util.HashMap;

import org.json.JSONObject;

import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.basic.constants.types.lol.TeamType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierDivisionType;

public class ParticipantData {
    public int id;
    public int summonerId;
    public int matchId;
    public boolean win;
    public String kda;
    public int champion;
    public LaneType lane;
    public TeamType side;
    public TierDivisionType rank;
    public int gain;
    public int damage;
    public int damageBuilding;
    public int healing;
    public int cs;
    public int goldEarned;
    public int ward;
    public int wardKilled;
    public int visionScore;
    public HashMap<String, Integer> pings;
    public JSONObject build;
}