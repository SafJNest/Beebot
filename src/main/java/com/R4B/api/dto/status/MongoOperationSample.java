package com.R4B.api.dto.status;

import com.R4B.api.dto.*;
public record MongoOperationSample(
    long at,
    MongoOperationRates rates
) {
}
