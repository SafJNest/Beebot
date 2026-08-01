package com.safjnest.lol.champion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.bson.Document;
import com.safjnest.lol.model.Filter;
import com.safjnest.nosql.MongoDB;

import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.basic.constants.types.lol.TeamType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierType;
import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;

public final class ChampionStatsProvider {

    public static final int EVENT_BATCH_SIZE = 100;

    private ChampionStatsProvider() {}

    public static void forEachMatch(
            Filter filter,
            Consumer<ChampionStatsData.RawMatchRead> matchConsumer,
            Consumer<ChampionStatsData.RawMatchRead> eventConsumer) {
        if (filter == null || matchConsumer == null || eventConsumer == null) return;
        List<String> matchIds = MongoDB.forEachChampionRawMatch(filter, raw -> {
            long materializeStarted = System.nanoTime();
            matchConsumer.accept(new ChampionStatsData.RawMatchRead(
                    rawMatch(raw.document()), raw.matchReadNanos(), raw.eventReadNanos(),
                    System.nanoTime() - materializeStarted));
        });
        try {
            MongoDB.forEachChampionRawMatchEventBatch(matchIds, EVENT_BATCH_SIZE, raw -> {
                long materializeStarted = System.nanoTime();
                eventConsumer.accept(new ChampionStatsData.RawMatchRead(
                    rawMatch(raw.document()), raw.matchReadNanos(), raw.eventReadNanos(),
                    System.nanoTime() - materializeStarted));
            });
        } finally {
            matchIds.clear();
        }
    }

    public static void forEachMatchWithBuild(
            Filter filter,
            BiConsumer<ChampionStatsData.RawMatchRead, Document> matchConsumer,
            Consumer<ChampionStatsData.RawMatchRead> eventConsumer) {
        if (filter == null || matchConsumer == null || eventConsumer == null) return;
        List<String> matchIds = MongoDB.forEachChampionRawMatchWithBuild(filter, raw -> {
            long materializeStarted = System.nanoTime();
            matchConsumer.accept(new ChampionStatsData.RawMatchRead(
                    rawMatch(raw.document()), raw.matchReadNanos(), raw.eventReadNanos(),
                    System.nanoTime() - materializeStarted), raw.document());
        });
        try {
            MongoDB.forEachChampionRawMatchEventBatch(matchIds, EVENT_BATCH_SIZE, raw -> {
                long materializeStarted = System.nanoTime();
                eventConsumer.accept(new ChampionStatsData.RawMatchRead(
                        rawMatch(raw.document()), raw.matchReadNanos(), raw.eventReadNanos(),
                        System.nanoTime() - materializeStarted));
            });
        } finally {
            matchIds.clear();
        }
    }

    public static void forEachTrendMatch(Filter filter, Consumer<List<ChampionStatsData.TrendParticipant>> consumer) {
        if (filter == null || consumer == null) return;
        MongoDB.forEachChampionTrendMatch(filter, participants -> {
            List<ChampionStatsData.TrendParticipant> result = new ArrayList<>(participants.size());
            for (Document participant : participants) {
                result.add(new ChampionStatsData.TrendParticipant(
                    participant.getInteger("champion", 0), lane(participant), participant.getBoolean("win", false)));
            }
            try {
                consumer.accept(result);
            } finally {
                result.clear();
            }
        });
    }

    private static ChampionStatsData.RawMatch rawMatch(Document document) {
        String matchId = String.valueOf(document.get("_id"));
        ChampionStatsData.MatchMeta metadata = new ChampionStatsData.MatchMeta(
                map(document.get("bans")), document.get("events"),
                number(document.get("timeStart")), number(document.get("timeEnd")),
                region(document.getString("region")), rank(document.getString("rank")));
        List<ChampionStatsData.RawParticipant> participants = new ArrayList<>();
        for (Document participant : participants(document.get("participants"))) {
            participants.add(new ChampionStatsData.RawParticipant(
                    participant.getInteger("champion", 0), lane(participant), participant.getBoolean("win", false),
                    team(participant), matchId, participant.getString("kda"), participant.getInteger("cs", 0),
                    participant.getInteger("goldEarned", 0), participant.getString("puuid")));
        }
        return new ChampionStatsData.RawMatch(matchId, metadata, participants);
    }

    private static List<Document> participants(Object value) {
        if (!(value instanceof List<?> values)) return List.of();
        List<Document> result = new ArrayList<>(values.size());
        for (Object item : values) if (item instanceof Document document) result.add(document);
        return result;
    }

    private static Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> source) || source.isEmpty()) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() != null) result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private static long number(Object value) {
        return value instanceof Number number ? number.longValue() : 0;
    }

    private static LaneType lane(Document participant) {
        try { return LaneType.valueOf(participant.getString("lane")); }
        catch (RuntimeException ignored) { return null; }
    }

    private static TeamType team(Document participant) {
        try { return TeamType.valueOf(participant.getString("team")); }
        catch (RuntimeException ignored) { return null; }
    }

    private static LeagueShard region(String value) {
        try { return value == null ? null : LeagueShard.valueOf(value); }
        catch (RuntimeException ignored) { return null; }
    }

    private static TierType rank(String value) {
        try { return value == null ? null : TierType.valueOf(value); }
        catch (RuntimeException ignored) { return null; }
    }
}
