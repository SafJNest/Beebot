package com.safjnest.spring.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.safjnest.spring.dto.LolProfileView;
import com.safjnest.spring.dto.LolSearchResult;
import com.safjnest.spring.service.LolApiService;
import com.safjnest.spring.util.LolRegionParser;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;

@RestController
@RequestMapping("/api/lol/{region}")
public class LolController {

    private final LolApiService lolApiService;

    public LolController() {
        this.lolApiService = new LolApiService();
    }

    @GetMapping("/search")
    public List<LolSearchResult> search(@PathVariable String region, @RequestParam String q) {
        if (q == null || q.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing search query");
        }

        LeagueShard shard = LolRegionParser.parse(region);
        return lolApiService.search(shard, q);
    }

    @GetMapping("/profile/{puuid}")
    public LolProfileView profile(@PathVariable String region, @PathVariable String puuid) {
        if (puuid == null || puuid.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing puuid");
        }

        LeagueShard shard = LolRegionParser.parse(region);
        LolProfileView profile = lolApiService.profile(shard, puuid);
        if (profile == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found");
        }
        return profile;
    }
}
