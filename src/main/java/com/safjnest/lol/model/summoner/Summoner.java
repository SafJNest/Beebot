package com.safjnest.lol.model.summoner;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.safjnest.nosql.AbstractEntity;

import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;

public class Summoner extends AbstractEntity<Summoner> {

    private final int summonerId;
    private final String puuid;
    private String riotId;
    private String region;
    private int level;
    private int icon;

    @JsonIgnore
    private String userId;
    @JsonIgnore
    private boolean tracking;
    @JsonIgnore
    private List<Rank> ranks = new ArrayList<>();
    @JsonIgnore
    private List<Mastery> masteries = new ArrayList<>();

    @JsonCreator
    public Summoner(
            @JsonProperty("summonerId") int summonerId,
            @JsonProperty("puuid") String puuid,
            @JsonProperty("riotId") String riotId,
            @JsonProperty("region") String region,
            @JsonProperty("level") int level,
            @JsonProperty("icon") int icon) {
        this.summonerId = summonerId;
        this.puuid = puuid;
        this.riotId = riotId;
        this.region = region;
        this.level = level;
        this.icon = icon;
    }

    public static Summoner hydrated(
            int summonerId,
            String puuid,
            String riotId,
            String region,
            int level,
            int icon,
            String userId,
            boolean tracking,
            List<Rank> ranks,
            List<Mastery> masteries) {
        Summoner summoner = new Summoner(summonerId, puuid, riotId, region, level, icon);
        summoner.userId = userId;
        summoner.tracking = tracking;
        summoner.ranks = ranks == null ? new ArrayList<>() : new ArrayList<>(ranks);
        summoner.masteries = masteries == null ? new ArrayList<>() : new ArrayList<>(masteries);
        summoner.markExisting();
        return summoner;
    }

    public int summonerId() {
        return summonerId;
    }

    public String puuid() {
        return puuid;
    }

    public String riotId() {
        return riotId;
    }

    public String region() {
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

    public List<Rank> ranks() {
        return List.copyOf(ranks);
    }

    public List<Mastery> masteries() {
        return List.copyOf(masteries);
    }

    public Summoner setRiotId(String riotId) {
        this.riotId = riotId;
        setValue("riotId", riotId);
        return this;
    }

    public Summoner setRegion(String region) {
        this.region = region;
        setValue("region", region);
        return this;
    }

    public Summoner setLevel(int level) {
        this.level = level;
        setValue("level", level);
        return this;
    }

    public Summoner setIcon(int icon) {
        this.icon = icon;
        setValue("icon", icon);
        return this;
    }

    public Summoner setRank(GameQueueType queue, Rank rank) {
        if (queue == null || rank == null) throw new IllegalArgumentException("Summoner rank queue and value are required");
        List<Rank> values = new ArrayList<>(ranks);
        boolean replaced = false;
        for (int index = 0; index < values.size(); index++) {
            if (queue.equals(values.get(index).queue())) {
                values.set(index, rank);
                replaced = true;
                break;
            }
        }
        if (!replaced) values.add(rank);
        ranks = values;
        replaceOrAppendArrayElement("ranks", "queue", queue.name(), rank);
        return this;
    }

    public Summoner setMasteries(List<Mastery> masteries) {
        this.masteries = masteries == null ? new ArrayList<>() : new ArrayList<>(masteries);
        setValue("masteries", this.masteries);
        return this;
    }

    public Summoner setTracking(boolean tracking) {
        this.tracking = tracking;
        setValue("tracking", tracking);
        return this;
    }

    public Summoner setTracking(String ownerId, boolean tracking) {
        if (ownerId == null || ownerId.isBlank()) throw new IllegalArgumentException("Summoner owner id is required");
        filterValue("userId", ownerId);
        this.tracking = tracking;
        setValue("tracking", tracking);
        return this;
    }

    public Summoner unlink(String ownerId) {
        if (ownerId == null || ownerId.isBlank()) throw new IllegalArgumentException("Summoner owner id is required");
        filterValue("userId", ownerId);
        userId = null;
        tracking = false;
        unsetValue("userId");
        setValue("tracking", false);
        return this;
    }

    public String name() {
        return riotIdPart(0);
    }

    public String tag() {
        return riotIdPart(1);
    }

    @Override
    protected String collectionName() {
        return "summoner";
    }

    @Override
    protected String entityId() {
        return puuid;
    }

    @Override
    protected Map<String, Object> snapshotValues() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("level", level);
        values.put("icon", icon);
        if (riotId != null) values.put("riotId", riotId);
        if (region != null) values.put("region", region);
        if (userId != null) values.put("userId", userId);
        if (tracking) values.put("tracking", true);
        if (!ranks.isEmpty()) values.put("ranks", ranks);
        if (!masteries.isEmpty()) values.put("masteries", masteries);
        return values;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof Summoner summoner)) return false;
        return summonerId == summoner.summonerId
                && level == summoner.level
                && icon == summoner.icon
                && Objects.equals(puuid, summoner.puuid)
                && Objects.equals(riotId, summoner.riotId)
                && Objects.equals(region, summoner.region);
    }

    @Override
    public int hashCode() {
        return Objects.hash(summonerId, puuid, riotId, region, level, icon);
    }

    @Override
    public String toString() {
        return "Summoner[summonerId=" + summonerId + ", puuid=" + puuid + ", riotId=" + riotId
                + ", region=" + region + ", level=" + level + ", icon=" + icon + "]";
    }

    private String riotIdPart(int index) {
        if (riotId == null || riotId.isBlank()) return "";
        String[] parts = riotId.split("#", 2);
        return index < parts.length ? parts[index] : "";
    }
}
