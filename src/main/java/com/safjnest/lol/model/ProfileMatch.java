package com.safjnest.lol.model;

import java.util.List;

import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;

public record ProfileMatch(
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
    List<ProfileMatchParticipant> participants
) {}
