package com.safjnest.nosql.writer;

import java.util.List;
import java.util.Map;

import com.safjnest.nosql.AbstractEntity;
import com.safjnest.lol.utils.PatchUtils;
import com.R4B.api.dto.*;
import com.R4B.api.dto.match.*;
import com.R4B.lol.*;

public final class MatchWriter extends AbstractEntity<MatchWriter> {
    private final Match data;
    public MatchWriter(Match data) { this.data = data; }
    public static MatchWriter existing(Match data) { MatchWriter writer = new MatchWriter(data); writer.markExisting(); return writer; }

    public MatchWriter setRank(TierType rank) {
        data.rank = rank;
        setValue("rank", rank);
        return this;
    }

    public MatchWriter setQueue(GameQueueType queue) {
        data.queue = queue;
        setValue("queue", queue);
        return this;
    }

    public MatchWriter setPatch(String patch) {
        data.patch = patch;
        setValue("patch", patch);
        if (patch == null || patch.isBlank()) unsetValue("patchMajor");
        else setValue("patchMajor", PatchUtils.patchMajor(patch));
        return this;
    }

    public MatchWriter setTimeStart(long timeStart) {
        data.timeStart = timeStart;
        setValue("timeStart", timeStart);
        return this;
    }

    public MatchWriter setTimeEnd(long timeEnd) {
        data.timeEnd = timeEnd;
        setValue("timeEnd", timeEnd);
        return this;
    }

    public MatchWriter setParticipant(Participant participant) {
        if (participant == null || participant.puuid == null || participant.puuid.isBlank()) {
            throw new IllegalArgumentException("Match participant puuid is required");
        }
        List<Participant> values = data.participants == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(data.participants);
        boolean replaced = false;
        for (int index = 0; index < values.size(); index++) {
            if (participant.puuid.equals(values.get(index).puuid)) {
                values.set(index, participant);
                replaced = true;
                break;
            }
        }
        if (!replaced) values.add(participant);
        data.participants = values;
        replaceOrAppendArrayElement("participants", "puuid", participant.puuid, participant);
        return this;
    }

    public MatchWriter setParticipantField(String puuid, String field, Object value) {
        Participant participant = findParticipant(puuid);
        if (participant == null) throw new IllegalArgumentException("Unknown match participant puuid=" + puuid);
        applyParticipantField(participant, field, value);
        setArrayElementField("participants", "puuid", puuid, field, value);
        return this;
    }

    @Override
    protected String collectionName() {
        return "match";
    }

    @Override
    protected String entityId() {
        if (data.gameId == null || data.gameId.isBlank()) throw new IllegalStateException("Match.gameId is required");
        return data.gameId;
    }

    @Override
    protected Map<String, Object> snapshotValues() {
        Map<String, Object> values = new java.util.LinkedHashMap<>();
        values.put("region", data.leagueShard);
        values.put("lastUpdate", data.lastUpdate);
        values.put("timeStart", data.timeStart);
        values.put("timeEnd", data.timeEnd);
        values.put("bans", data.bans == null ? Map.of() : data.bans);
        values.put("participants", data.participants == null ? List.of() : data.participants);
        if (data.queue != null) values.put("queue", data.queue);
        if (data.rank != null) values.put("rank", data.rank);
        if (data.patch != null) {
            values.put("patch", data.patch);
            values.put("patchMajor", PatchUtils.patchMajor(data.patch));
        }
        return values;
    }

    private Participant findParticipant(String puuid) {
        if (data.participants == null || puuid == null) return null;
        for (Participant participant : data.participants) if (participant != null && puuid.equals(participant.puuid)) return participant;
        return null;
    }

    private static void applyParticipantField(Participant participant, String field, Object value) {
        switch (field) {
            case "win" -> participant.win = booleanValue(value);
            case "kda" -> participant.kda = stringValue(value);
            case "champion" -> participant.champion = NumberUtils.parseInt(value);
            case "rankProgress" -> participant.rankProgress = rankProgressValue(value);
            case "damage" -> participant.damage = NumberUtils.parseInt(value);
            case "cs" -> participant.cs = NumberUtils.parseInt(value);
            case "goldEarned" -> participant.goldEarned = NumberUtils.parseInt(value);
            case "visionScore" -> participant.visionScore = NumberUtils.parseInt(value);
            default -> throw new IllegalArgumentException("Unsupported participant field=" + field);
        }
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
        Integer lp = values.get("lp") == null ? null : NumberUtils.parseInt(values.get("lp"));
        if (rank == null || lp == null) return null;
        RankProgress progress = new RankProgress(rank, lp,
                values.get("gain") == null ? null : NumberUtils.parseInt(values.get("gain")),
                enumValue(values.get("previousRank"), TierDivisionType.class),
                values.get("previousLp") == null ? null : NumberUtils.parseInt(values.get("previousLp")));
        return progress;
    }
}
