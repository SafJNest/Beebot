package com.safjnest.spring.controller;

import java.util.Locale;
import java.util.StringJoiner;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.safjnest.lol.model.Filter;
import com.safjnest.lol.model.ActivityFilter;
import com.safjnest.lol.utils.GameQueueTypeUtils;
import com.safjnest.lol.utils.LaneTypeUtils;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierType;

public final class LolApiParameters {

    private static final GameQueueType DEFAULT_QUEUE = GameQueueType.TEAM_BUILDER_RANKED_SOLO;
    private static final int MIN_LEADERBOARD_LIMIT = 1;
    private static final int MAX_LEADERBOARD_LIMIT = 50;

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

    public static GameQueueType activityQueue(String value) {
        return optionalQueue(value);
    }

    public static GameQueueType optionalQueue(String value) {
        if (value == null || value.isBlank() || "ALL".equalsIgnoreCase(value.trim())) return null;
        return parseEnum(value, GameQueueType.class, "queue");
    }

    public static String patch(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (!normalized.matches("\\d+\\.\\d+")) throw invalid("patch", "must have the form major.minor");
        return normalized;
    }

    public static int minGames(int value) {
        if (value < 1) throw invalid("minGames", "must be greater than 0");
        return value;
    }

    public static ActivityFilter matchupsFilter(
        long start,
        long end,
        String queueValue,
        String patchValue,
        String roleValue,
        int minGamesValue
    ) {
        validateMatchupsPeriod(start, end);
        GameQueueType queue = optionalQueue(queueValue);
        LaneType role = role(roleValue);
        validateRole(queue, role);
        ActivityFilter filter = new ActivityFilter();
        filter.setQueue(queue);
        filter.setLane(role);
        filter.setMinGames(minGames(minGamesValue));
        if (start != 0 && end != 0) filter.setPeriod(start, end).setPatch(null);
        else filter.setPatch(patch(patchValue));
        return filter;
    }

    public static Filter activityFilter(long start, long end, GameQueueType queue, int champion) {
        if (start < 0) throw invalid("start", "must be greater than or equal to 0");
        if (end < 0) throw invalid("end", "must be greater than or equal to 0");
        if (start != 0 && end != 0 && end < start) throw invalid("end", "must be greater than or equal to start");
        if (champion < 0) throw invalid("champion", "must be greater than or equal to 0");
        return Filter.summoner(start, end).setQueue(queue).setChampion(champion);
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

    public static int limit(int limit) {
        if (limit < MIN_LEADERBOARD_LIMIT || limit > MAX_LEADERBOARD_LIMIT) {
            throw invalid("limit", "must be between 1 and 50");
        }
        return limit;
    }

    public static String requiredText(String value, String fieldName) {
        if (value == null || value.isBlank()) throw invalid(fieldName, "is required");
        return value.trim();
    }

    public static ResponseStatusException invalid(String parameter, String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid " + parameter + ": " + reason);
    }

    // ============================================================================

    private static void validateMatchupsPeriod(long start, long end) {
        if (start < 0) throw invalid("start", "must be greater than or equal to 0");
        if (end < 0) throw invalid("end", "must be greater than or equal to 0");
        if ((start == 0) != (end == 0)) throw invalid("start/end", "must be provided together");
        if (start != 0 && end < start) throw invalid("end", "must be greater than or equal to start");
    }

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
