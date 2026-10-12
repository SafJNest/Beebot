package com.R4B.api.dto.status;

import com.R4B.api.dto.*;
public record MongoOperationRates(
    double insert,
    double query,
    double update,
    double delete,
    double command,
    double getmore,
    double total
) {
}
