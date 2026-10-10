package com.hive.status;

public record JobProgress(
    int current,
    int total
) {
}
