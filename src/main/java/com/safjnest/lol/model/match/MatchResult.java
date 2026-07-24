package com.safjnest.lol.model.match;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;

public class MatchResult {

    public final String gameId;
    public final GameQueueType queue;
    public final long timeStart;
    public final long timeEnd;
    public final boolean win;
    public final String kda;
    public final int championId;
    public final LaneType lane;
    public final int damage;
    public final int cs;
    public final int gold;
    public final int vision;
    public final int teamKills;
    public final List<Integer> items;
    public final List<Integer> summonerSpells;
    public final List<Participant> participants;

    @JsonCreator
    public MatchResult(
        @JsonProperty("gameId") String gameId,
        @JsonProperty("queue") GameQueueType queue,
        @JsonProperty("timeStart") long timeStart,
        @JsonProperty("timeEnd") long timeEnd,
        @JsonProperty("win") boolean win,
        @JsonProperty("kda") String kda,
        @JsonProperty("championId") int championId,
        @JsonProperty("lane") LaneType lane,
        @JsonProperty("damage") int damage,
        @JsonProperty("cs") int cs,
        @JsonProperty("gold") int gold,
        @JsonProperty("vision") int vision,
        @JsonProperty("teamKills") int teamKills,
        @JsonProperty("items") List<Integer> items,
        @JsonProperty("summonerSpells") List<Integer> summonerSpells,
        @JsonProperty("participants") List<? extends Participant> participants
    ) {
        this.gameId = gameId;
        this.queue = queue;
        this.timeStart = timeStart;
        this.timeEnd = timeEnd;
        this.win = win;
        this.kda = kda;
        this.championId = championId;
        this.lane = lane;
        this.damage = damage;
        this.cs = cs;
        this.gold = gold;
        this.vision = vision;
        this.teamKills = teamKills;
        this.items = items != null ? List.copyOf(items) : List.of();
        this.summonerSpells = summonerSpells != null ? List.copyOf(summonerSpells) : List.of();
        this.participants = participants != null ? List.copyOf(participants) : List.of();
    }

    public static MatchResult of(
        String gameId,
        GameQueueType queue,
        long timeStart,
        long timeEnd,
        boolean win,
        String kda,
        int championId,
        LaneType lane,
        int damage,
        int cs,
        int gold,
        int vision,
        int teamKills,
        List<Integer> items,
        List<Integer> summonerSpells,
        List<? extends Participant> participants
    ) {
        return new MatchResult(gameId, queue, timeStart, timeEnd, win, kda, championId, lane, damage, cs, gold, vision,
            teamKills, items, summonerSpells, participants);
    }

    public String gameId() { return gameId; }
    public GameQueueType queue() { return queue; }
    public long timeStart() { return timeStart; }
    public long timeEnd() { return timeEnd; }
    public boolean win() { return win; }
    public String kda() { return kda; }
    public int championId() { return championId; }
    public LaneType lane() { return lane; }
    public int damage() { return damage; }
    public int cs() { return cs; }
    public int gold() { return gold; }
    public int vision() { return vision; }
    public int teamKills() { return teamKills; }
    public List<Integer> items() { return items; }
    public List<Integer> summonerSpells() { return summonerSpells; }
    public List<Participant> participants() { return participants; }
}
