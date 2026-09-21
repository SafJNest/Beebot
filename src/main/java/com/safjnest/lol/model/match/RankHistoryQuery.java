package com.safjnest.lol.model.match;

import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;

public record RankHistoryQuery(
    GameQueueType queue,
    RankHistoryView view,
    Integer season,
    String patch,
    long timeStart,
    long timeEnd,
    MatchOrder order
) {}
