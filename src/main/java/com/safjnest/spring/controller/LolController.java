package com.safjnest.spring.controller;

import java.util.List;
import java.util.Locale;

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
import com.safjnest.lol.service.LeagueService;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;

@RestController
@RequestMapping("/api/lol/{shard}")
public class LolController {

    private final LolApiService lolApiService;

    public LolController() {
        this.lolApiService = new LolApiService();
    }

    @GetMapping("/search")
    public List<LolSearchResult> search(
            @PathVariable("shard") String shardValue,
            @RequestParam("q") String q
    ) {
        String query = requireText(q, "search query");
        if (LeagueService.normalizeSearch(query).isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Search query must contain at least one character after removing spaces, '-' and '#'"
            );
        }

        return lolApiService.search(parseShard(shardValue), query);
    }

    @GetMapping("/profile/{puuid}")
    public LolProfileView profile(
            @PathVariable("shard") String shardValue,
            @PathVariable("puuid") String puuid
    ) {
        LolProfileView profile = lolApiService.profile(
            parseShard(shardValue),
            requireText(puuid, "puuid")
        );

        if (profile == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found");
        }

        return profile;
    }

    @GetMapping("/profile-by-name/{gameName}/{tagLine}")
    public LolProfileView profileByName(
            @PathVariable("shard") String shardValue,
            @PathVariable("gameName") String gameName,
            @PathVariable("tagLine") String tagLine
    ) {
        LolProfileView profile = lolApiService.profileByName(
            parseShard(shardValue),
            requireText(gameName, "game name"),
            requireText(tagLine, "tag line")
        );

        if (profile == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found");
        }

        return profile;
    }

    static LeagueShard parseShard(String value) {
        if (value == null || value.isBlank()) {
            throw invalidShard(value);
        }

        try {
            LeagueShard shard = LeagueShard.valueOf(value.trim().toUpperCase(Locale.ROOT));
            if (shard == LeagueShard.UNKNOWN) {
                throw invalidShard(value);
            }
            return shard;
        } catch (IllegalArgumentException e) {
            throw invalidShard(value);
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing " + fieldName);
        }
        return value.trim();
    }

    private static ResponseStatusException invalidShard(String value) {
        StringBuilder validShards = new StringBuilder();
        for (LeagueShard shard : LeagueShard.values()) {
            if (shard == LeagueShard.UNKNOWN) continue;
            if (validShards.length() > 0) validShards.append(", ");
            validShards.append(shard.name());
        }
        return new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Invalid LeagueShard '" + value + "'. Expected one of: " + validShards
        );
    }
}
