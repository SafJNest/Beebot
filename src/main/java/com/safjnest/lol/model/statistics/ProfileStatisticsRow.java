package com.safjnest.lol.model.statistics;

import com.R4B.api.dto.*;
import com.R4B.api.dto.statistics.*;
import com.R4B.api.dto.statistics.shared.*;
public record ProfileStatisticsRow(
    long timeStart,
    long timeEnd,
    String data
) {}
