package com.safjnest.spring.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.safjnest.lol.model.Filter;
import com.safjnest.lol.model.record.RecordMetric;
import com.safjnest.lol.model.record.RecordPage;
import com.safjnest.lol.model.record.RecordsOverview;
import com.safjnest.lol.service.ProfileRecordService;

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
        return profileRecordService.getGlobalOverview(Filter.canonical(), LolApiParameters.region(regionValue));
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
            Filter.canonical(),
            metric,
            LolApiParameters.region(regionValue),
            LolApiParameters.matchLimit(limit),
            LolApiParameters.matchOffset(offset)
        );
    }
}
