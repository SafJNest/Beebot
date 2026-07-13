package com.safjnest.spring.controller;

import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.safjnest.spring.dto.LolProfileView;
import com.safjnest.spring.dto.LolSearchResult;
import com.safjnest.spring.dto.LolApiError;
import com.safjnest.lol.service.LeagueService;
import com.safjnest.lol.model.MatchLookup;
import com.safjnest.lol.service.ProfilePageService;
import com.safjnest.lol.model.ProfilePageData;
import com.safjnest.spring.util.LolApiMapper;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;

@RestController
@RequestMapping("/api/lol/{shard}")
public class LolController {

    private final ProfilePageService profilePageService;

    public LolController() {
        this.profilePageService = new ProfilePageService();
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

        return LeagueService.searchSummoners(query, parseShard(shardValue)).stream().map(LolApiMapper::toSearchResult).toList();
    }

    @GetMapping("/profile/{puuid}")
    public LolProfileView profile(
            @PathVariable("shard") String shardValue,
            @PathVariable("puuid") String puuid
    ) {
        ProfilePageData page = profilePageService.get(parseShard(shardValue), requireText(puuid, "puuid"));

        if (page == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found");
        }
        return LolApiMapper.toProfileView(page);
    }

    @GetMapping("/profile-by-name/{gameName}/{tagLine}")
    public LolProfileView profileByName(
            @PathVariable("shard") String shardValue,
            @PathVariable("gameName") String gameName,
            @PathVariable("tagLine") String tagLine
    ) {
        ProfilePageData page = profilePageService.get(parseShard(shardValue), requireText(gameName, "game name"), requireText(tagLine, "tag line"));

        if (page == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found");
        }
        return LolApiMapper.toProfileView(page);
    }

    @GetMapping("/match/{gameId}")
    public ResponseEntity<?> match(
            @PathVariable("shard") String shardValue,
            @PathVariable("gameId") String gameId
    ) {
        MatchLookup lookup = LeagueService.getMatchDetail(requireText(gameId, "match id"), parseShard(shardValue));

        return switch (lookup.getStatus()) {
            case READY -> ResponseEntity.ok(lookup.getMatch());
            case PENDING -> ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new LolApiError(HttpStatus.ACCEPTED.value(), "match_pending", "Match analysis is pending"));
            case NOT_FOUND -> throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Match not found");
        };
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
