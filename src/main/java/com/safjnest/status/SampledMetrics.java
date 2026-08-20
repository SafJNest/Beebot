package com.safjnest.status;

import com.safjnest.lol.model.status.JvmMetrics;
import com.safjnest.lol.model.status.RedisMetrics;
import com.safjnest.lol.model.status.SystemMetrics;

record SampledMetrics(
    JvmMetrics jvm,
    SystemMetrics system,
    RedisMetrics redis
) {

    static SampledMetrics empty() {
        return new SampledMetrics(null, null, null);
    }
}
