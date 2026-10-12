package com.R4B.api.dto.summoner;

import com.R4B.api.dto.*;
public record SummonerLeaderboard(
    long position,
    SummonerView summoner
) {}
