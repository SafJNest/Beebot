package com.safjnest.spring.nosql.writer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.R4B.api.dto.summoner.*;
import com.R4B.lol.*;
import com.safjnest.spring.nosql.AbstractEntity;
public final class SummonerWriter extends AbstractEntity<SummonerWriter> {
    private final Summoner data;
    public SummonerWriter(Summoner data) { this.data = data; }
    public static SummonerWriter existing(Summoner data) { SummonerWriter writer = new SummonerWriter(data); writer.markExisting(); return writer; }
    public SummonerWriter setRiotId(String value) { data.setRiotId(value); setValue("riotId", value); return this; }
    public SummonerWriter setRegion(LeagueShard value) { data.setRegion(value); setValue("region", value); return this; }
    public SummonerWriter setLevel(int value) { data.setLevel(value); setValue("level", value); return this; }
    public SummonerWriter setIcon(int value) { data.setIcon(value); setValue("icon", value); return this; }
    public SummonerWriter setRank(GameQueueType queue, Rank rank) { data.setRank(queue, rank); setValue("ranks." + GameQueueType.canonicalQueue(queue).name(), rank); return this; }
    public SummonerWriter setMasteries(List<Mastery> values) { data.setMasteries(values); setValue("masteries", values); return this; }
    public SummonerWriter setTracking(boolean value) { data.setTracking(value); setValue("tracking", value); return this; }
    public SummonerWriter setTracking(String ownerId, boolean value) { requireOwner(ownerId); filterValue("userId", ownerId); return setTracking(value); }
    public SummonerWriter unlink(String ownerId) { requireOwner(ownerId); filterValue("userId", ownerId); data.clearUserId(); data.setTracking(false); unsetValue("userId"); setValue("tracking", false); return this; }
    // ============================================================================
    @Override protected String collectionName() { return "summoner"; }
    @Override protected String entityId() { return data.puuid(); }
    @Override protected Map<String, Object> snapshotValues() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("level", data.level()); values.put("icon", data.icon());
        if (data.riotId() != null) values.put("riotId", data.riotId());
        if (data.region() != null) values.put("region", data.region().name());
        if (data.userId() != null) values.put("userId", data.userId());
        if (data.tracking()) values.put("tracking", true);
        if (data.ranks() != null) values.put("ranks", data.ranks());
        if (data.masteries() != null && !data.masteries().isEmpty()) values.put("masteries", data.masteries());
        return values;
    }
    private static void requireOwner(String value) { if (value == null || value.isBlank()) throw new IllegalArgumentException("Summoner owner id is required"); }
}
