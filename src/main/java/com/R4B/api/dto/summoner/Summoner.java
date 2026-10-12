package com.R4B.api.dto.summoner;

import com.R4B.api.dto.*;
import com.R4B.lol.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Summoner {

    @JsonProperty("puuid")
    private final String puuid;
    @JsonProperty("riotId")
    private String riotId;
    @JsonProperty("region")
    private LeagueShard region;
    @JsonProperty("level")
    private int level;
    @JsonProperty("icon")
    private int icon;

    @JsonIgnore
    private String userId;
    @JsonIgnore
    private boolean tracking;
    @JsonIgnore
    private Map<GameQueueType, Rank> ranks;
    @JsonIgnore
    private List<Mastery> masteries;

    @JsonCreator
    public Summoner(
            @JsonProperty("puuid") String puuid,
            @JsonProperty("riotId") String riotId,
            @JsonProperty("region") LeagueShard region,
            @JsonProperty("level") int level,
            @JsonProperty("icon") int icon) {
        this.puuid = puuid;
        this.riotId = riotId;
        this.region = region;
        this.level = level;
        this.icon = icon;
    }

    public static Summoner hydrated(
            String puuid,
            String riotId,
            LeagueShard region,
            int level,
            int icon,
            String userId,
            boolean tracking,
            Map<GameQueueType, Rank> ranks,
            List<Mastery> masteries) {
        Summoner summoner = new Summoner(puuid, riotId, region, level, icon);
        summoner.userId = userId;
        summoner.tracking = tracking;
        summoner.ranks = ranks == null ? null : new LinkedHashMap<>(ranks);
        summoner.masteries = masteries == null ? null : new ArrayList<>(masteries);
        return summoner;
    }

    public String puuid() {
        return puuid;
    }

    public String riotId() {
        return riotId;
    }

    public LeagueShard region() {
        return region;
    }

    public int level() {
        return level;
    }

    public int icon() {
        return icon;
    }

    public String userId() {
        return userId;
    }

    public boolean tracking() {
        return tracking;
    }

    public Map<GameQueueType, Rank> ranks() {
        return ranks == null ? null : Map.copyOf(ranks);
    }

    public List<Mastery> masteries() {
        return masteries == null ? null : List.copyOf(masteries);
    }

    public Summoner setRiotId(String riotId) {
        this.riotId = riotId;
        return this;
    }

    public Summoner setRegion(LeagueShard region) {
        this.region = region;
        return this;
    }

    public Summoner setLevel(int level) {
        this.level = level;
        return this;
    }

    public Summoner setIcon(int icon) {
        this.icon = icon;
        return this;
    }

    public Summoner setRank(GameQueueType queue, Rank rank) {
        if (queue == null || rank == null) throw new IllegalArgumentException("Summoner rank queue and value are required");
        GameQueueType canonicalQueue = GameQueueType.canonicalQueue(queue);
        if (ranks == null) ranks = new LinkedHashMap<>();
        ranks.put(canonicalQueue, rank);
        return this;
    }

    public Summoner setMasteries(List<Mastery> masteries) {
        this.masteries = masteries == null ? null : new ArrayList<>(masteries);
        return this;
    }

    public Summoner setTracking(boolean tracking) {
        this.tracking = tracking;
        return this;
    }

    public Summoner setTracking(String ownerId, boolean tracking) {
        if (ownerId == null || ownerId.isBlank()) throw new IllegalArgumentException("Summoner owner id is required");
        this.tracking = tracking;
        return this;
    }

    public String name() {
        return riotIdPart(0);
    }

    public String tag() {
        return riotIdPart(1);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof Summoner summoner)) return false;
        return level == summoner.level
                && icon == summoner.icon
                && Objects.equals(puuid, summoner.puuid)
                && Objects.equals(riotId, summoner.riotId)
                && region == summoner.region;
    }

    @Override
    public int hashCode() {
        return Objects.hash(puuid, riotId, region, level, icon);
    }

    @Override
    public String toString() {
        return "Summoner[puuid=" + puuid + ", riotId=" + riotId
                + ", region=" + region + ", level=" + level + ", icon=" + icon + "]";
    }

    private String riotIdPart(int index) {
        if (riotId == null || riotId.isBlank()) return "";
        String[] parts = riotId.split("#", 2);
        return index < parts.length ? parts[index] : "";
    }

    @JsonIgnore public String getPUUID() { return puuid(); }
    @JsonIgnore public LeagueShard getPlatform() { return region(); }
    @JsonIgnore public int getSummonerLevel() { return level(); }
    @JsonIgnore public int getProfileIconId() { return icon(); }
    @JsonIgnore public String getName() { return name(); }

    public void clearUserId() { userId = null; }
}
