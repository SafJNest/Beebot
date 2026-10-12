package com.R4B.api.dto.summoner;

import com.R4B.api.dto.*;
public record Mastery(
    int championId,
    int level,
    int points
) {}
