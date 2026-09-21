package com.safjnest.spring.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.safjnest.lol.model.ApiResult;
import com.safjnest.lol.service.ChampionService;

import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;

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
}
