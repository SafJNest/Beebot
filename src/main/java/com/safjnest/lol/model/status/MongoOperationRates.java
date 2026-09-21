package com.safjnest.lol.model.status;

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
