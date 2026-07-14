package com.safjnest.spring.controller;

import java.util.Locale;
import java.util.StringJoiner;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.safjnest.lol.model.leaderboard.LeaderboardDistribution;
import com.safjnest.lol.model.leaderboard.LeaderboardPage;
import com.safjnest.lol.service.LeaderboardService;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierType;

@RestController
@RequestMapping("/api/lol")
public class LeaderboardController {

    private final LeaderboardService leaderboardService;

    public LeaderboardController() {
        this.leaderboardService = new LeaderboardService();
    }

    @GetMapping("/leaderboard")
    public LeaderboardPage leaderboard(
            @RequestParam("rank") String rankValue,
            @RequestParam(value = "region", required = false) String regionValue,
            @RequestParam(value = "queue", required = false) String queueValue,
            @RequestParam(value = "page", defaultValue = "1") int page
    ) {
        if (page < 1) throw invalidParameter("page", "must be greater than 0");
        return leaderboardService.getLeaderboard(
            parseRank(rankValue), parseQueue(queueValue), parseRegion(regionValue), page
        );
    }

    @GetMapping("/leaderboard/rank-distribution")
    public LeaderboardDistribution rankDistribution(
            @RequestParam(value = "region", required = false) String regionValue,
            @RequestParam(value = "queue", required = false) String queueValue
    ) {
        return leaderboardService.getRankDistribution(parseQueue(queueValue), parseRegion(regionValue));
    }

    @GetMapping("/leaderboard/top-regions")
    public LeaderboardDistribution topRegions(
            @RequestParam("rank") String rankValue,
            @RequestParam(value = "queue", required = false) String queueValue
    ) {
        return leaderboardService.getTopRegions(parseQueue(queueValue), parseRank(rankValue));
    }

    static TierType parseRank(String value) {
        if (value == null || value.isBlank()) throw invalidParameter("rank", "is required");
        try {
            return TierType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw invalidParameter("rank", "must be one of: " + enumNames(TierType.values()));
        }
    }

    static GameQueueType parseQueue(String value) {
        if (value == null || value.isBlank()) return GameQueueType.TEAM_BUILDER_RANKED_SOLO;
        try {
            return GameQueueType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw invalidParameter("queue", "must be one of: " + enumNames(GameQueueType.values()));
        }
    }

    static String parseRegion(String value) {
        if (value == null || value.isBlank()) return LeaderboardService.GLOBAL_REGION;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (LeaderboardService.GLOBAL_REGION.equals(normalized)) return normalized;
        try {
            LeagueShard shard = LeagueShard.valueOf(normalized);
            if (shard == LeagueShard.UNKNOWN) throw invalidRegion(value);
            return shard.name();
        } catch (IllegalArgumentException exception) {
            throw invalidRegion(value);
        }
    }

    private static ResponseStatusException invalidRegion(String value) {
        return invalidParameter("region", "must be GLOBAL or one of: " + enumNames(LeagueShard.values()));
    }

    private static ResponseStatusException invalidParameter(String parameter, String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid " + parameter + ": " + reason);
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
