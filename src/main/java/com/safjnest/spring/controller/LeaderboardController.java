package com.safjnest.spring.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
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
            @RequestParam("rank") String rankValue,
            @RequestParam(value = "region", required = false) String regionValue,
            @RequestParam(value = "queue", required = false) String queueValue,
            @RequestParam(value = "page", defaultValue = "1") int page
    ) {
        return LolApiResponses.from(
            leaderboardService.getLeaderboard(
                LolApiParameters.requiredRank(rankValue),
                LolApiParameters.queue(queueValue),
                LolApiParameters.region(regionValue),
                LolApiParameters.page(page)
            ),
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
