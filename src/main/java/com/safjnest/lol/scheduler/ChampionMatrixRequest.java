package com.safjnest.lol.scheduler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.safjnest.lol.model.Filter;
import com.safjnest.lol.service.ChampionService;

final class ChampionMatrixRequest {

    private final Map<String, Filter> buildFilters;
    private CompletableFuture<ChampionService.MatrixRefreshResult> future;
    private boolean running;

    ChampionMatrixRequest() {
        buildFilters = new HashMap<>();
    }

    synchronized boolean tryAddBuild(Filter filter) {
        if (filter == null) return true;
        if (running) return false;
        buildFilters.putIfAbsent(filter.toKey(), Filter.fromStateKey(filter.toStateKey()));
        return true;
    }

    synchronized List<Filter> startAndGetBuildFilters() {
        running = true;
        return List.copyOf(buildFilters.values());
    }

    synchronized void setFuture(CompletableFuture<ChampionService.MatrixRefreshResult> value, java.util.concurrent.CompletionStage<?> termination, Runnable cleanup) {
        future = value;
        termination.whenComplete((result, failure) -> cleanup.run());
    }

    synchronized CompletableFuture<ChampionService.MatrixRefreshResult> future() {
        return future;
    }

}
