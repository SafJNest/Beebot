package com.safjnest.spring.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.safjnest.lol.model.ApiResult;
import com.safjnest.lol.service.LeagueService;
import com.safjnest.lol.model.summoner.SummonerView;
import com.safjnest.lol.service.ProfilePageService;

@RestController
@RequestMapping("/api/lol/{shard}")
public class LolController {

    private final ProfilePageService profilePageService;

    public LolController() {
        this.profilePageService = new ProfilePageService();
    }

    @GetMapping("/search")
    public List<SummonerView> search(
            @PathVariable("shard") String shardValue,
            @RequestParam("q") String q
    ) {
        String query = LolApiParameters.requiredText(q, "search query");
        if (LeagueService.normalizeSearch(query).isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Search query must contain at least one character after removing spaces, '-' and '#'"
            );
        }

        return LeagueService.searchSummoners(query, LolApiParameters.requiredShard(shardValue));
    }

    @GetMapping("/profile/{puuid}")
    public SummonerView profile(
            @PathVariable("shard") String shardValue,
            @PathVariable("puuid") String puuid
    ) {
        SummonerView page = profilePageService.get(
            LolApiParameters.requiredShard(shardValue),
            LolApiParameters.requiredText(puuid, "puuid")
        );

        if (page == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found");
        }
        return page;
    }

    @GetMapping("/profile-by-name/{gameName}/{tagLine}")
    public SummonerView profileByName(
            @PathVariable("shard") String shardValue,
            @PathVariable("gameName") String gameName,
            @PathVariable("tagLine") String tagLine
    ) {
        SummonerView page = profilePageService.get(
            LolApiParameters.requiredShard(shardValue),
            LolApiParameters.requiredText(gameName, "game name"),
            LolApiParameters.requiredText(tagLine, "tag line")
        );

        if (page == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found");
        }
        return page;
    }

    @GetMapping("/match/{gameId}")
    public ResponseEntity<?> match(
            @PathVariable("shard") String shardValue,
            @PathVariable("gameId") String gameId
    ) {
        ApiResult<?> result = LeagueService.getMatchDetail(
            LolApiParameters.requiredText(gameId, "match id"),
            LolApiParameters.requiredShard(shardValue)
        );
        return LolApiResponses.from(
            result,
            "match_pending",
            "Match analysis is pending",
            "Match not found"
        );
    }
}
