package com.safjnest.spring.controller;

import com.safjnest.spring.lol.service.FilterDefaults;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.R4B.api.dto.record.RecordPage;
import com.R4B.api.dto.record.RecordsOverview;
import com.R4B.lol.RecordMetric;
import com.safjnest.spring.lol.service.ProfileRecordService;

@RestController
@RequestMapping("/api/lol/records")
public class RecordsController {

    private final ProfileRecordService profileRecordService;

    public RecordsController() {
        this.profileRecordService = new ProfileRecordService();
    }

    @GetMapping
    public RecordsOverview overview(
            @RequestParam(name = "region", required = false) String regionValue
    ) {
        return profileRecordService.getGlobalOverview(FilterDefaults.canonical(), LolApiParameters.region(regionValue));
    }

    @GetMapping("/HIGHEST_MASTERY/{champion}")
    public RecordPage highestMasteryByChampion(
            @PathVariable("champion") String championValue,
            @RequestParam(name = "region", required = false) String regionValue,
            @RequestParam(name = "limit", defaultValue = "20") int limit,
            @RequestParam(name = "offset", defaultValue = "0") int offset
    ) {
        return profileRecordService.getGlobalPage(
            FilterDefaults.canonical(),
            RecordMetric.HIGHEST_MASTERY,
            LolApiParameters.region(regionValue),
            LolApiParameters.champion(championValue),
            LolApiParameters.matchLimit(limit),
            LolApiParameters.matchOffset(offset)
        );
    }

    @GetMapping("/{metric}")
    public RecordPage metric(
            @PathVariable("metric") String metricValue,
            @RequestParam(name = "region", required = false) String regionValue,
            @RequestParam(name = "limit", defaultValue = "20") int limit,
            @RequestParam(name = "offset", defaultValue = "0") int offset
    ) {
        RecordMetric metric = LolApiParameters.recordMetric(metricValue);
        return profileRecordService.getGlobalPage(
            FilterDefaults.canonical(),
            metric,
            LolApiParameters.region(regionValue),
            LolApiParameters.matchLimit(limit),
            LolApiParameters.matchOffset(offset)
        );
    }
}
