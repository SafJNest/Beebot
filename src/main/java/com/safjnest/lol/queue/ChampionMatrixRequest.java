package com.safjnest.lol.queue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.safjnest.lol.model.Filter;
import com.safjnest.lol.service.ChampionService;

final class ChampionMatrixRequest {

    private final ConcurrentMap<String, Filter> buildFilters;
    private CompletableFuture<ChampionService.MatrixRefreshResult> future;
    private boolean running;

    ChampionMatrixRequest() {
        buildFilters = new ConcurrentHashMap<>();
    }

    synchronized void addBuild(Filter filter) {
        if (filter != null) buildFilters.putIfAbsent(filter.toKey(), Filter.fromStateKey(filter.toStateKey()));
    }

    synchronized void start() {
        running = true;
    }

    synchronized boolean running() {
        return running;
    }

    synchronized void setFuture(CompletableFuture<ChampionService.MatrixRefreshResult> value) {
        future = value;
    }

    synchronized CompletableFuture<ChampionService.MatrixRefreshResult> future() {
        return future;
    }

    List<Filter> buildFilters() {
        return new ArrayList<>(buildFilters.values());
    }
}
