package com.safjnest.lol.model.status;

public record MongoOperationSample(
    long at,
    MongoOperationRates rates
) {
}
