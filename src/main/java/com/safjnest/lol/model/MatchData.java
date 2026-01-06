package com.safjnest.lol.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.json.JSONObject;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.TeamType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierType;
import no.stelar7.api.r4j.pojo.lol.match.v5.ChampionBan;
import no.stelar7.api.r4j.pojo.lol.match.v5.LOLMatch;
import no.stelar7.api.r4j.pojo.lol.match.v5.MatchTeam;

public class MatchData {

    public MatchData() {}

    public MatchData(LOLMatch match) {
        this.gameId = String.valueOf(match.getGameId());
        this.region = match.getPlatform();
        this.queue = match.getQueue();
        this.timeStart = match.getGameCreation();
        this.timeEnd = match.getGameEndTimestamp();
        this.patch = match.getGameVersion();

        this.bans = new HashMap<>();
        for (MatchTeam team : match.getTeams()) {
            List<Integer> list = new ArrayList<>();
            for (ChampionBan champion : team.getBans()) {
                if (champion.getChampionId() != -1) list.add(champion.getChampionId());
            }
            bans.put(team.getTeamId(), list);
        }
    }


    public int id; //TODO: remove
    public String gameId;
    public LeagueShard region;
    public GameQueueType queue;
    public TierType rank;
    public HashMap<TeamType, List<Integer>> bans;      
    public JSONObject events;
    public long timeStart;
    public long timeEnd;
    public String patch;
    public List<ParticipantData> participants;

    /**
     * milliseconds
     * @return
     */
    public long getDuration() {
        return timeEnd - timeStart;
    }
}