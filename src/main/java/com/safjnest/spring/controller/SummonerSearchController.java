package com.safjnest.spring.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.safjnest.lol.model.summoner.SummonerView;
import com.safjnest.lol.service.SummonerService;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;

@RestController
@RequestMapping("/api/lol")
public class SummonerSearchController {

    @GetMapping("/search")
    public List<SummonerView> search(
            @RequestParam("q") String q,
            @RequestParam(name = "shard", required = false) String shardValue
    ) {
        String query = LolApiParameters.requiredText(q, "search query");
        if (SummonerService.normalizeSearch(query).isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Search query must contain at least one character after removing spaces, '-' and '#'"
            );
        }

        LeagueShard shard = LolApiParameters.optionalShard(shardValue);
        return SummonerService.search(query, shard);
    }
}
