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
import com.safjnest.lol.model.ActivityFilter;
import com.safjnest.lol.model.Filter;
import com.safjnest.lol.model.statistics.ProfileActivity;
import com.safjnest.lol.model.statistics.ProfileMatchups;
import com.safjnest.lol.service.MatchService;
import com.safjnest.lol.model.summoner.SummonerView;
import com.safjnest.lol.service.ProfileService;
import com.safjnest.lol.service.SummonerService;

@RestController
@RequestMapping("/api/lol/{shard}")
public class LolController {

    private final ProfileService profileService;

    public LolController() {
        this.profileService = new ProfileService();
    }

    @GetMapping("/search")
    public List<SummonerView> search(
            @PathVariable("shard") String shardValue,
            @RequestParam("q") String q
    ) {
        String query = LolApiParameters.requiredText(q, "search query");
        if (SummonerService.normalizeSearch(query).isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Search query must contain at least one character after removing spaces, '-' and '#'"
            );
        }

        return SummonerService.search(query, LolApiParameters.requiredShard(shardValue));
    }

    @GetMapping("/profile/{puuid}")
    public ResponseEntity<?> profile(
            @PathVariable("shard") String shardValue,
            @PathVariable("puuid") String puuid
    ) {
        ApiResult<SummonerView> result = profileService.get(
            LolApiParameters.requiredShard(shardValue),
            LolApiParameters.requiredText(puuid, "puuid")
        );
        return LolApiResponses.from(result, "profile_pending", "Profile initialization is pending", "Profile not found");
    }

    @GetMapping("/profile/{puuid}/activity")
    public ProfileActivity activity(
            @PathVariable("shard") String shardValue,
            @PathVariable("puuid") String puuid,
            @RequestParam(name = "start", defaultValue = "0") long start,
            @RequestParam(name = "end", defaultValue = "0") long end,
            @RequestParam(name = "queue", required = false) String queueValue,
            @RequestParam(name = "champion", defaultValue = "0") int champion
    ) {
        Filter filter = LolApiParameters.activityFilter(
            start,
            end,
            LolApiParameters.activityQueue(queueValue),
            champion
        );
        return profileService.getActivity(
            LolApiParameters.requiredShard(shardValue),
            LolApiParameters.requiredText(puuid, "puuid"),
            filter
        );
    }

    @GetMapping("/profile/{puuid}/matchups")
    public ResponseEntity<?> matchups(
            @PathVariable("shard") String shardValue,
            @PathVariable("puuid") String puuid,
            @RequestParam(name = "start", defaultValue = "0") long start,
            @RequestParam(name = "end", defaultValue = "0") long end,
            @RequestParam(name = "queue", required = false) String queueValue,
            @RequestParam(name = "patch", required = false) String patchValue,
            @RequestParam(name = "role", required = false) String roleValue,
            @RequestParam(name = "minGames", defaultValue = "5") int minGames
    ) {
        ActivityFilter filter = LolApiParameters.matchupsFilter(start, end, queueValue, patchValue, roleValue, minGames);
        ApiResult<ProfileMatchups> result = profileService.getMatchups(
            LolApiParameters.requiredShard(shardValue),
            LolApiParameters.requiredText(puuid, "puuid"),
            filter
        );
        return LolApiResponses.from(
            result,
            "profile_matchups_pending",
            "Profile matchups are being prepared",
            "Profile not found"
        );
    }

    @GetMapping("/profile-by-name/{gameName}/{tagLine}")
    public ResponseEntity<?> profileByName(
            @PathVariable("shard") String shardValue,
            @PathVariable("gameName") String gameName,
            @PathVariable("tagLine") String tagLine
    ) {
        ApiResult<SummonerView> result = profileService.get(
            LolApiParameters.requiredShard(shardValue),
            LolApiParameters.requiredText(gameName, "game name"),
            LolApiParameters.requiredText(tagLine, "tag line")
        );
        return LolApiResponses.from(result, "profile_pending", "Profile initialization is pending", "Profile not found");
    }

    @GetMapping("/match/{gameId}")
    public ResponseEntity<?> match(
            @PathVariable("shard") String shardValue,
            @PathVariable("gameId") String gameId
    ) {
        ApiResult<?> result = MatchService.getDetail(
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
