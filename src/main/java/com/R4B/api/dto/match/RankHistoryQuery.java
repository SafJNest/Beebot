package com.R4B.api.dto.match;

import com.R4B.api.dto.*;
import com.R4B.lol.GameQueueType;
import com.R4B.lol.MatchOrder;
import com.R4B.lol.RankHistoryView;

public record RankHistoryQuery(
    GameQueueType queue,
    RankHistoryView view,
    Integer season,
    String patch,
    long timeStart,
    long timeEnd,
    MatchOrder order
) {}
