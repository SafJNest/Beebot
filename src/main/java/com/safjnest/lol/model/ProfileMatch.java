package com.safjnest.lol.model;

import java.util.List;

public record ProfileMatch(
    String gameId,
    String queue,
    long timeStart,
    long timeEnd,
    boolean win,
    String kda,
    int championId,
    String lane,
    int damage,
    int cs,
    int gold,
    int vision,
    List<Integer> items,
    List<Integer> summonerSpells
) {}
