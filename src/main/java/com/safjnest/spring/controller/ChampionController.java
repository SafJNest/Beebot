package com.safjnest.spring.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.R4B.api.dto.ApiResult;
import com.R4B.api.dto.ChampionStatistics;
import com.R4B.api.dto.Filter;
import com.R4B.lol.GameQueueType;
import com.R4B.lol.LaneType;
import com.safjnest.lol.service.ChampionService;
import com.safjnest.lol.scheduler.ComputeScheduler;

@RestController
@RequestMapping("/api/lol")
public class ChampionController {

    private final ChampionService championService;

    public ChampionController() {
        this.championService = new ChampionService();
    }

    @GetMapping("/champion/indexables")
    public ResponseEntity<?> indexables() {
        return ResponseEntity.ok(championService.getIndexables());
    }

    @GetMapping("/champions/statistics")
    public List<ChampionStatistics> statistics(@RequestParam("filter") String filterValue) {
        Filter filter = Filter.fromStateKey(LolApiParameters.requiredText(filterValue, "filter"));
        return List.copyOf(championService.getStatisticsAll(filter).values());
    }

    @org.springframework.web.bind.annotation.PostMapping("/champions/statistics/refresh")
    public CompletableFuture<Map<String, Integer>> refreshStatisticsMatrix(
            @RequestParam("patch") String patchValue,
            @RequestParam("queue") String queueValue
    ) {
        String patch = LolApiParameters.patch(patchValue);
        GameQueueType queue = LolApiParameters.queue(LolApiParameters.requiredText(queueValue, "queue"));
        if (patch == null) throw new org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.BAD_REQUEST, "Patch is required");
        if (queue == null) throw new org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.BAD_REQUEST, "Queue is required");
        return ComputeScheduler.enqueueChampionStatsMatrix(patch, queue).thenApply(result -> Map.of(
            "combinations", result.combinations(),
            "skipped", result.skipped(),
            "generated", result.generated(),
            "empty", result.empty(),
            "champions", result.persistedChampions()
        ));
    }

    @GetMapping("/champion/{champion}/statistics")
    public ChampionStatistics statistics(
            @PathVariable("champion") String championValue,
            @RequestParam("filter") String filterValue
    ) {
        Filter filter = Filter.fromStateKey(LolApiParameters.requiredText(filterValue, "filter"));
        return championService.getStatistics(filter.setChampion(championId(championValue)));
    }

    @GetMapping("/champion/{champion}/build")
    public com.R4B.api.dto.Build build(
            @PathVariable("champion") String championValue,
            @RequestParam("filter") String filterValue
    ) {
        Filter filter = Filter.fromStateKey(LolApiParameters.requiredText(filterValue, "filter"));
        return championService.getBuild(filter.setChampion(championId(championValue)));
    }

    @GetMapping("/champions/tier-list")
    public ResponseEntity<?> tierList(
            @RequestParam(value = "patch", required = false) String patchValue,
            @RequestParam(value = "rank", required = false) String rankValue,
            @RequestParam(value = "region", required = false) String regionValue,
            @RequestParam(value = "queue", required = false) String queueValue
    ) {
        ApiResult<?> result = championService.getTierList(
            LolApiParameters.patch(patchValue),
            LolApiParameters.rank(rankValue),
            LolApiParameters.region(regionValue),
            LolApiParameters.queue(queueValue)
        );
        return LolApiResponses.from(
            result,
            "champion_tier_list_pending",
            "Champion tier list is being prepared",
            "Champion tier list not found"
        );
    }

    @GetMapping("/champion/{champion}")
    public ResponseEntity<?> champion(
            @PathVariable("champion") String championValue,
            @RequestParam(value = "patch", required = false) String patchValue,
            @RequestParam(value = "rank", required = false) String rankValue,
            @RequestParam(value = "region", required = false) String regionValue,
            @RequestParam(value = "queue", required = false) String queueValue,
            @RequestParam(value = "role", required = false) String roleValue
    ) {
        GameQueueType queue = LolApiParameters.queue(queueValue);
        LaneType role = LolApiParameters.role(roleValue);
        LolApiParameters.validateRole(queue, role);
        ApiResult<?> result = championService.get(
            championValue,
            LolApiParameters.patch(patchValue),
            LolApiParameters.rank(rankValue),
            LolApiParameters.region(regionValue),
            queue,
            role
        );
        return LolApiResponses.from(
            result,
            "champion_data_pending",
            "Champion data is being prepared",
            "Champion not found"
        );
    }

    private static int championId(String championValue) {
        var champion = com.safjnest.lol.utils.ChampionUtils.findChampion(championValue);
        if (champion == null) throw new org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.NOT_FOUND, "Champion not found");
        return champion.getId();
    }
}
