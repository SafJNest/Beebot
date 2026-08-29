package com.safjnest.spring.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.safjnest.lol.model.ApiResult;
import com.safjnest.lol.model.ResponseMetadata;
import com.safjnest.lol.model.leaderboard.LeaderboardPage;
import com.safjnest.lol.model.leaderboard.LeaderboardDistribution;
import com.safjnest.lol.service.LeaderboardService;

@RestController
@RequestMapping("/api/lol")
public class LeaderboardController {

    private final LeaderboardService leaderboardService;

    public LeaderboardController() {
        this.leaderboardService = new LeaderboardService();
    }

    @GetMapping("/leaderboard")
    public ResponseEntity<?> leaderboard(
            @RequestParam(value = "rank", required = false) String rankValue,
            @RequestParam(value = "region", required = false) String regionValue,
            @RequestParam(value = "queue", required = false) String queueValue,
            @RequestParam(value = "role", required = false) String roleValue,
            @RequestParam(value = "otp", required = false) String otpValue,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "limit", defaultValue = "50") int limit
    ) {
        var queue = LolApiParameters.queue(queueValue);
        var role = LolApiParameters.role(roleValue);
        LolApiParameters.validateRole(queue, role);
        ApiResult<LeaderboardPage> result = leaderboardService.getLeaderboard(
                LolApiParameters.rank(rankValue), queue, LolApiParameters.region(regionValue), role,
                LolApiParameters.otpChampion(otpValue),
                LolApiParameters.page(page),
                LolApiParameters.limit(limit)
            );
        if (result.payload() != null) {
            LeaderboardPage pageValue = result.payload();
            ResponseMetadata metadata = new ResponseMetadata(
                new ResponseMetadata.Pagination(pageValue.page(), pageValue.pageSize(), null, null,
                    pageValue.total(), pageValue.pages(), null),
                null, false, null
            );
            result = ApiResult.ready(pageValue.withMetadata(metadata), metadata);
        }
        return LolApiResponses.from(
            result,
            "leaderboard_pending",
            "Leaderboard data is being prepared",
            "Leaderboard not found"
        );
    }

    @GetMapping("/leaderboard/rank-distribution")
    public LeaderboardDistribution rankDistribution(
            @RequestParam(value = "region", required = false) String regionValue,
            @RequestParam(value = "queue", required = false) String queueValue
    ) {
        return leaderboardService.getRankDistribution(
            LolApiParameters.queue(queueValue),
            LolApiParameters.region(regionValue)
        );
    }

    @GetMapping("/leaderboard/top-regions")
    public LeaderboardDistribution topRegions(
            @RequestParam("rank") String rankValue,
            @RequestParam(value = "queue", required = false) String queueValue
    ) {
        return leaderboardService.getTopRegions(
            LolApiParameters.queue(queueValue),
            LolApiParameters.requiredRank(rankValue)
        );
    }
}
