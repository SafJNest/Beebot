package com.safjnest.lol.model;

/** Mastery data stored alongside a tracked summoner. */
public record ProfileMastery(
    int championId,
    int level,
    int points
) {}
