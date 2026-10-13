package com.safjnest.spring.status;

import com.R4B.api.dto.status.JvmMetrics;
import com.R4B.api.dto.status.MongoMetrics;
import com.R4B.api.dto.status.RedisMetrics;
import com.R4B.api.dto.status.SystemMetrics;

record SampledMetrics(
    JvmMetrics jvm,
    SystemMetrics system,
    RedisMetrics redis,
    MongoMetrics mongo
) {

    static SampledMetrics empty() {
        return new SampledMetrics(null, null, null, null);
    }
}
