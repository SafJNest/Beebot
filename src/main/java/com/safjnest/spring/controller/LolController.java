package com.safjnest.spring.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.safjnest.lol.model.ApiResult;
import com.safjnest.lol.model.ActivityFilter;
import com.safjnest.lol.model.Filter;
import com.safjnest.lol.model.ResponseMetadata;
import com.safjnest.lol.model.match.LiveGame;
import com.safjnest.lol.model.match.Match;
import com.safjnest.lol.model.match.MatchPage;
import com.safjnest.lol.model.match.MatchOrder;
import com.safjnest.lol.model.match.RankHistory;
import com.safjnest.lol.model.match.RankHistoryQuery;
import com.safjnest.lol.model.statistics.ProfileActivity;
import com.safjnest.lol.model.statistics.ProfileMatchups;
import com.safjnest.lol.model.record.RecordMetric;
import com.safjnest.lol.model.record.RecordPage;
import com.safjnest.lol.service.MatchService;
import com.safjnest.lol.model.summoner.SummonerView;
import com.safjnest.lol.service.ProfileRecordService;
import com.safjnest.lol.service.ProfileService;
import com.safjnest.lol.service.SummonerService;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;

@RestController
@RequestMapping("/api/lol/{shard}")
public class LolController {

    private final ProfileService profileService;
    private final ProfileRecordService profileRecordService;

    public LolController() {
        this.profileService = new ProfileService();
        this.profileRecordService = new ProfileRecordService();
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
        return profileResponse(result);
    }

    @PostMapping("/profile/{puuid}/refresh")
    public ResponseEntity<Void> refreshProfile(
            @PathVariable("shard") String shardValue,
            @PathVariable("puuid") String puuid
    ) {
        LeagueShard shard = LolApiParameters.requiredShard(shardValue);
        String profilePuuid = LolApiParameters.requiredText(puuid, "puuid");
        if (SummonerService.find(profilePuuid, shard) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found");
        }

        SummonerService.refresh(profilePuuid, shard);
        ProfileService.markManuallySeen(profilePuuid);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/profile/{puuid}/matches")
    public MatchPage matches(
            @PathVariable("shard") String shardValue,
            @PathVariable("puuid") String puuid,
            @RequestParam(name = "queue", required = false) String queueValue,
            @RequestParam(name = "limit", defaultValue = "20") int limit,
            @RequestParam(name = "offset", defaultValue = "0") int offset,
            @RequestParam(name = "timeStart", defaultValue = "0") long timeStart,
            @RequestParam(name = "timeEnd", defaultValue = "0") long timeEnd,
            @RequestParam(name = "sort", required = false) String sortValue
    ) {
        LeagueShard shard = LolApiParameters.requiredShard(shardValue);
        String profilePuuid = LolApiParameters.requiredText(puuid, "puuid");
        int pageLimit = LolApiParameters.matchLimit(limit);
        int pageOffset = LolApiParameters.matchOffset(offset);
        MatchOrder order = LolApiParameters.matchOrder(sortValue);
        Filter filter = LolApiParameters.activityFilter(
            timeStart,
            timeEnd,
            LolApiParameters.optionalQueue(queueValue),
            0
        );
        MatchPage page = MatchService.getPage(
            profilePuuid,
            shard,
            filter.timeStart(),
            filter.timeEnd(),
            filter.queue(),
            pageOffset,
            pageLimit,
            order
        );
        ResponseMetadata metadata = new ResponseMetadata(
            new ResponseMetadata.Pagination(null, null, page.limit(), page.offset(), page.total(), null, page.hasMore()),
            null, false, filter
        );
        return page.withMetadata(metadata);
    }

    @GetMapping("/profile/{puuid}/rank-history")
    public RankHistory rankHistory(
            @PathVariable("shard") String shardValue,
            @PathVariable("puuid") String puuid,
            @RequestParam(name = "queue", required = false) String queueValue,
            @RequestParam(name = "view", required = false) String viewValue,
            @RequestParam(name = "season", required = false) Integer seasonValue,
            @RequestParam(name = "patch", required = false) String patchValue,
            @RequestParam(name = "timeStart", defaultValue = "0") long timeStart,
            @RequestParam(name = "timeEnd", defaultValue = "0") long timeEnd,
            @RequestParam(name = "sort", required = false) String sortValue
    ) {
        LeagueShard shard = LolApiParameters.requiredShard(shardValue);
        String profilePuuid = LolApiParameters.requiredText(puuid, "puuid");
        RankHistoryQuery query = LolApiParameters.rankHistoryQuery(
            queueValue,
            viewValue,
            seasonValue,
            patchValue,
            timeStart,
            timeEnd,
            sortValue
        );
        return MatchService.getRankHistory(profilePuuid, shard, query);
    }

    @GetMapping("/profile/{puuid}/activity")
    public ResponseEntity<?> activity(
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
        ApiResult<ProfileActivity> result = profileService.getActivity(
            LolApiParameters.requiredShard(shardValue),
            LolApiParameters.requiredText(puuid, "puuid"),
            filter
        );
        return LolApiResponses.from(
            result,
            "profile_activity_pending",
            "Profile activity is being prepared",
            "Profile not found"
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

    @GetMapping("/profile/{puuid}/records")
    public ResponseEntity<?> records(
            @PathVariable("shard") String shardValue,
            @PathVariable("puuid") String puuid
    ) {
        return LolApiResponses.from(
            profileRecordService.get(
                LolApiParameters.requiredText(puuid, "puuid"),
                LolApiParameters.requiredShard(shardValue),
                Filter.canonical()
            ),
            "profile_records_pending",
            "Profile records are being prepared",
            "Profile not found"
        );
    }

    @GetMapping("/records/{metric}")
    public RecordPage globalRecords(
            @PathVariable("metric") String metricValue,
            @RequestParam(name = "region", required = false) String regionValue,
            @RequestParam(name = "limit", defaultValue = "20") int limit,
            @RequestParam(name = "offset", defaultValue = "0") int offset
    ) {
        RecordMetric metric = LolApiParameters.recordMetric(metricValue);
        return profileRecordService.getGlobal(
            Filter.canonical(),
            metric,
            LolApiParameters.region(regionValue),
            LolApiParameters.matchLimit(limit),
            LolApiParameters.matchOffset(offset)
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
        return profileResponse(result);
    }

    @GetMapping("/livegame/{puuid}")
    public LiveGame liveGame(
            @PathVariable("shard") String shardValue,
            @PathVariable("puuid") String puuid
    ) {
        LiveGame game = SummonerService.getLiveGame(
            LolApiParameters.requiredText(puuid, "puuid"),
            LolApiParameters.requiredShard(shardValue)
        );
        if (game == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Summoner not found");
        return game;
    }

    @GetMapping("/livegame-by-name/{gameName}/{tagLine}")
    public LiveGame liveGameByName(
            @PathVariable("shard") String shardValue,
            @PathVariable("gameName") String gameName,
            @PathVariable("tagLine") String tagLine
    ) {
        LiveGame game = SummonerService.getLiveGame(
            LolApiParameters.requiredText(gameName, "game name"),
            LolApiParameters.requiredText(tagLine, "tag line"),
            LolApiParameters.requiredShard(shardValue)
        );
        if (game == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Summoner not found");
        return game;
    }

    @GetMapping("/match/{gameId}")
    public ResponseEntity<?> match(
            @PathVariable("shard") String shardValue,
            @PathVariable("gameId") String gameId
    ) {
        ApiResult<Match> result = MatchService.getDetail(
            LolApiParameters.requiredText(gameId, "match id"),
            LolApiParameters.requiredShard(shardValue)
        );
        if (result.status() == ApiResult.Status.READY && result.payload() != null) {
            Match match = result.payload().withMetadata(ResponseMetadata.ready(result.payload().lastUpdate, null));
            result = ApiResult.ready(match, match.metadata);
        } else if (result.status() == ApiResult.Status.PENDING) {
            result = ApiResult.pending(new ResponseMetadata(null, null, true, null));
        }
        return LolApiResponses.from(
            result,
            "match_pending",
            "Match analysis is pending",
            "Match not found"
        );
    }

    private static ResponseEntity<?> profileResponse(ApiResult<SummonerView> result) {
        ResponseEntity<?> response = LolApiResponses.from(
            result, "profile_pending", "Profile initialization is pending", "Profile not found");
        return result.metadata() != null && Boolean.TRUE.equals(result.metadata().refresh())
            ? ResponseEntity.status(response.getStatusCode()).headers(response.getHeaders())
                .header("X-Profile-Refresh", "true").body(response.getBody())
            : response;
    }
}
