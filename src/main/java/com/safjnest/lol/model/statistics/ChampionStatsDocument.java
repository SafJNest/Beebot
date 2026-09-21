package com.safjnest.lol.model.statistics;

import com.safjnest.lol.model.statistics.shared.ChampionNode;
import com.safjnest.lol.model.statistics.shared.ChampionStatsScope;

import java.util.LinkedHashMap;
import java.util.Map;

public class ChampionStatsDocument {

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
