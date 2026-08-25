package com.safjnest.lol.model.match;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.safjnest.nosql.AbstractEntity;
import com.safjnest.lol.tracker.Tracker;
import com.safjnest.lol.model.ResponseMetadata;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.TeamType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierDivisionType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierType;
import no.stelar7.api.r4j.pojo.lol.match.v5.LOLMatch;

public class Match extends AbstractEntity<Match> {

    public int id;
    public String gameId;
    public LeagueShard leagueShard;
    public GameQueueType queue;
    public TierType rank;
    public long lastUpdate;
    public ResponseMetadata metadata;
    public Map<TeamType, List<Integer>> bans = new HashMap<>();
    @JsonIgnore
    public JSONObject events;
    @JsonProperty("events")
    public Map<String, Object> eventData;
    public long timeStart;
    public long timeEnd;
    public String patch;
    public List<Participant> participants;

    public static Match hydrated() {
        Match match = new Match();
        match.markExisting();
        return match;
    }

    public static Match fromR4J(LOLMatch source) {
        return source == null ? null : Tracker.fromR4J(source);
    }

    public static Match fromR4J(
            LOLMatch source,
            Map<String, HashMap<String, String>> matchData) {
        return source == null ? null : Tracker.fromR4J(source, matchData);
    }

    public Match setRank(TierType rank) {
        this.rank = rank;
        setValue("rank", rank);
        return this;
    }

    public Match setQueue(GameQueueType queue) {
        this.queue = queue;
        setValue("queue", queue);
        return this;
    }

    public Match setPatch(String patch) {
        this.patch = patch;
        setValue("patch", patch);
        if (patch == null || patch.isBlank()) unsetValue("patchMajor");
        else setValue("patchMajor", patchMajor(patch));
        return this;
    }

    public Match setTimeStart(long timeStart) {
        this.timeStart = timeStart;
        setValue("timeStart", timeStart);
        return this;
    }

    public Match setTimeEnd(long timeEnd) {
        this.timeEnd = timeEnd;
        setValue("timeEnd", timeEnd);
        return this;
    }

    public Match setParticipant(Participant participant) {
        if (participant == null || participant.puuid == null || participant.puuid.isBlank()) {
            throw new IllegalArgumentException("Match participant puuid is required");
        }
        List<Participant> values = participants == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(participants);
        boolean replaced = false;
        for (int index = 0; index < values.size(); index++) {
            if (participant.puuid.equals(values.get(index).puuid)) {
                values.set(index, participant);
                replaced = true;
                break;
            }
        }
        if (!replaced) values.add(participant);
        participants = values;
        replaceOrAppendArrayElement("participants", "puuid", participant.puuid, participant);
        return this;
    }

    public Match setParticipantField(String puuid, String field, Object value) {
        Participant participant = findParticipant(puuid);
        if (participant == null) throw new IllegalArgumentException("Unknown match participant puuid=" + puuid);
        applyParticipantField(participant, field, value);
        setArrayElementField("participants", "puuid", puuid, field, value);
        return this;
    }

    @JsonIgnore
    public long getDuration() {
        return timeEnd - timeStart;
    }

    public void restoreEvents() {
        if (events == null) events = new JSONObject(eventData != null ? eventData : Map.of());
    }

    public Match withMetadata(ResponseMetadata value) {
        metadata = value;
        return this;
    }

    @Override
    protected String collectionName() {
        return "match";
    }

    @Override
    protected String entityId() {
        if (gameId == null || gameId.isBlank()) throw new IllegalStateException("Match.gameId is required");
        return gameId;
    }

    @Override
    protected Map<String, Object> snapshotValues() {
        Map<String, Object> values = new java.util.LinkedHashMap<>();
        values.put("region", leagueShard);
        values.put("lastUpdate", lastUpdate);
        values.put("timeStart", timeStart);
        values.put("timeEnd", timeEnd);
        values.put("bans", bans == null ? Map.of() : bans);
        values.put("participants", participants == null ? List.of() : participants);
        if (queue != null) values.put("queue", queue);
        if (rank != null) values.put("rank", rank);
        if (patch != null) {
            values.put("patch", patch);
            values.put("patchMajor", patchMajor(patch));
        }
        return values;
    }

    private static String patchMajor(String patch) {
        String value = patch == null ? null : patch.trim();
        if (value == null || value.isBlank()) return null;
        int firstSeparator = value.indexOf('.');
        if (firstSeparator < 0) return value;
        int secondSeparator = value.indexOf('.', firstSeparator + 1);
        return secondSeparator < 0 ? value : value.substring(0, secondSeparator);
    }

    private Participant findParticipant(String puuid) {
        if (participants == null || puuid == null) return null;
        for (Participant participant : participants) if (participant != null && puuid.equals(participant.puuid)) return participant;
        return null;
    }

    private static void applyParticipantField(Participant participant, String field, Object value) {
        switch (field) {
            case "win" -> participant.win = booleanValue(value);
            case "kda" -> participant.kda = stringValue(value);
            case "champion" -> participant.champion = intValue(value);
            case "rankProgress" -> participant.rankProgress = rankProgressValue(value);
            case "damage" -> participant.damage = intValue(value);
            case "cs" -> participant.cs = intValue(value);
            case "goldEarned" -> participant.goldEarned = intValue(value);
            case "visionScore" -> participant.visionScore = intValue(value);
            default -> throw new IllegalArgumentException("Unsupported participant field=" + field);
        }
    }

    private static int intValue(Object value) {
        return value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value));
    }

    private static boolean booleanValue(Object value) {
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value));
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static <T extends Enum<T>> T enumValue(Object value, Class<T> type) {
        if (value == null) return null;
        if (type.isInstance(value)) return type.cast(value);
        return Enum.valueOf(type, String.valueOf(value));
    }

    private static RankProgress rankProgressValue(Object value) {
        if (value instanceof RankProgress progress) return progress;
        if (!(value instanceof Map<?, ?> values)) return null;
        TierDivisionType rank = enumValue(values.get("rank"), TierDivisionType.class);
        Integer lp = values.get("lp") == null ? null : intValue(values.get("lp"));
        if (rank == null || lp == null) return null;
        RankProgress progress = new RankProgress(rank, lp,
                values.get("gain") == null ? null : intValue(values.get("gain")),
                enumValue(values.get("previousRank"), TierDivisionType.class),
                values.get("previousLp") == null ? null : intValue(values.get("previousLp")));
        return progress;
    }
}
