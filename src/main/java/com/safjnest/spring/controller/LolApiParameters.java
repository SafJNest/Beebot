package com.safjnest.spring.controller;

import java.util.Locale;
import java.util.StringJoiner;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.safjnest.lol.utils.GameQueueTypeUtils;
import com.safjnest.lol.utils.LaneTypeUtils;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierType;

public final class LolApiParameters {

    private static final GameQueueType DEFAULT_QUEUE = GameQueueType.TEAM_BUILDER_RANKED_SOLO;

    private LolApiParameters() {}

    public static TierType rank(String value) {
        if (value == null || value.isBlank()) return null;
        return parseEnum(value, TierType.class, "rank");
    }

    public static TierType requiredRank(String value) {
        if (value == null || value.isBlank()) throw invalid("rank", "is required");
        return parseEnum(value, TierType.class, "rank");
    }

    public static GameQueueType queue(String value) {
        if (value == null || value.isBlank()) return DEFAULT_QUEUE;
        return parseEnum(value, GameQueueType.class, "queue");
    }

    public static LeagueShard region(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        LeagueShard shard = parseEnum(normalized, LeagueShard.class, "region");
        if (shard == LeagueShard.UNKNOWN) throw invalid("region", "must be a valid League shard");
        return shard;
    }

    public static LeagueShard requiredShard(String value) {
        if (value == null || value.isBlank()) throw invalid("shard", "is required");
        return region(value);
    }

    public static LaneType role(String value) {
        if (value == null || value.isBlank()) return null;
        LaneType role = parseEnum(value, LaneType.class, "role");
        if (!LaneTypeUtils.playables().contains(role)) {
            throw invalid("role", "must be one of: TOP, JUNGLE, MID, BOT, UTILITY");
        }
        return role;
    }

    public static void validateRole(GameQueueType queue, LaneType role) {
        if (role != null && !GameQueueTypeUtils.hasLane(queue)) {
            throw invalid("role", "is not supported by the selected queue");
        }
    }

    public static int page(int page) {
        if (page < 1) throw invalid("page", "must be greater than 0");
        return page;
    }

    public static String requiredText(String value, String fieldName) {
        if (value == null || value.isBlank()) throw invalid(fieldName, "is required");
        return value.trim();
    }

    public static ResponseStatusException invalid(String parameter, String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid " + parameter + ": " + reason);
    }

    // ============================================================================

    private static <T extends Enum<T>> T parseEnum(String value, Class<T> type, String parameter) {
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw invalid(parameter, "must be one of: " + enumNames(type.getEnumConstants()));
        }
    }

    private static <T extends Enum<T>> String enumNames(T[] values) {
        StringJoiner names = new StringJoiner(", ");
        for (T value : values) {
            if ("UNKNOWN".equals(value.name())) continue;
            names.add(value.name());
        }
        return names.toString();
    }
}
