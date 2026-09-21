package com.safjnest.lol.model;

public class ActivityFilter extends Filter {

    private static final int DEFAULT_MIN_GAMES = 5;

    private int minGames = DEFAULT_MIN_GAMES;

    public ActivityFilter() {
        super();
        setChampion(0);
        setLane(null);
        setQueue(null);
        setRank(null);
        setPatch(null);
        setRegion(null);
        setOpponent(0);
        setDuo(0);
        setPeriod(Filter.canonical().period());
    }

    public int minGames() {
        return minGames;
    }

    public ActivityFilter setMinGames(int minGames) {
        if (minGames < 1) throw new IllegalArgumentException("minGames must be greater than 0");
        this.minGames = minGames;
        return this;
    }

    public Filter aggregationFilter() {
        return Filter.fromStateKey(toStateKey());
    }
}
