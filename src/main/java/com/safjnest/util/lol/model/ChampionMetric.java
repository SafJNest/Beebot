package com.safjnest.util.lol.model;

import com.safjnest.sql.QueryRecord;
import com.safjnest.sql.database.LeagueDB;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;

public class ChampionMetric {

    private int champion;
    private LaneType lane;
    private LeagueShard region;
    private String patch;


    private int games;
    private double winrate;
    private double banrate;
    private double pickrate;

    private long lastUpdate;

    public ChampionMetric(int champion, LaneType lane, LeagueShard region, String patch) {
        this.champion = champion;
        this.lane = lane;
        this.region = region;
        this.patch = patch;
        this.games = 0;
        this.winrate = 0;
        this.banrate = 0;
        this.pickrate = 0;
        this.lastUpdate = 0;
    }

    public ChampionMetric(QueryRecord record) {
        this.champion = record.getAsInt("champion");
        this.lane = record.getAsLaneType("lane");
        this.region = record.getAsLeagueShard("region");
        this.patch = record.get("patch");
        this.games = record.getAsInt("games");
        this.winrate = record.getAsDouble("winrate");
        this.banrate = record.getAsDouble("banrate");
        this.pickrate = record.getAsDouble("pickrate");
        this.lastUpdate = record.getAsEpochSecond("last_update");
    }

    public int getChampion() {
        return champion;
    }

    public LaneType getLane() {
        return lane;
    }

    public LeagueShard getRegion() {
        return region;
    }

    public String getPatch() {
        return patch;
    }

    public int getGames() {
        return games;
    }

    public void setGames(int games) {
        this.games = games;
    }

    public double getWinrate() {
        return winrate;
    }

    public void setWinrate(double winrate) {
        this.winrate = winrate;
    }

    public double getBanrate() {
        return banrate;
    }

    public void setBanrate(double banrate) {
        this.banrate = banrate;
    }

    public double getPickrate() {
        return pickrate;
    }

    public void setPickrate(double pickrate) {
        this.pickrate = pickrate;
    }

    public long getLastUpdate() {
        return lastUpdate;
    }

    public void setLastUpdate(long lastUpdate) {
        this.lastUpdate = lastUpdate;
    }

    public void update() {
      this.lastUpdate = System.currentTimeMillis() / 1000L;
      LeagueDB.upsertChampionMetric(this);
      
    }
}