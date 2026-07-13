package com.safjnest.lol.model;

public record ProfileMatchParticipant(
    int championId,
    String puuid,
    String team
) {}
