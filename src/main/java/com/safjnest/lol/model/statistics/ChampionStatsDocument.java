package com.safjnest.lol.model.statistics;

import com.safjnest.lol.model.statistics.shared.ChampionNode;
import com.safjnest.lol.model.statistics.shared.ChampionStatsScope;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
public class ChampionStatsDocument {

    @com.fasterxml.jackson.annotation.JsonIgnore
    public static final List<String> ARENA_SUBTEAM_IDS = List.of("1", "2", "3", "4", "5", "6", "7", "8");

    @com.fasterxml.jackson.annotation.JsonIgnore
    public static final List<String> ARENA_PLACEMENTS = List.of("1", "2", "3", "4", "5", "6", "7", "8");

    public String _id;

    public ChampionStatsScope scope;

    public long games;
    public long banGames;
    public String previousPatch;

    public boolean ready;
    public long updatedAt;

    public Map<Integer, ChampionNode> champions = new LinkedHashMap<>();

    public ChampionStatsDocument() {}

    public ChampionStatsDocument(ChampionStatsScope scope, long games, long banGames, String previousPatch) {
        this.scope = scope;
        this._id = scope.toKey();
        this.games = games;
        this.banGames = banGames;
        this.previousPatch = previousPatch;
        this.ready = true;
        this.updatedAt = System.currentTimeMillis();
    }

    public ChampionNode champion(int championId) {
        return champions.get(championId);
    }
}
