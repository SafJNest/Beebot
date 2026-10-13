package com.safjnest.lol.client;

import java.lang.reflect.Type;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.concurrent.CompletableFuture;

import com.R4B.api.dto.match.LiveGame;
import com.R4B.api.dto.match.Match;
import com.R4B.api.dto.match.MatchPage;
import com.R4B.api.dto.match.RankHistory;
import com.R4B.api.dto.record.ProfileRecordPage;
import com.R4B.api.dto.Build;
import com.R4B.api.dto.ChampionStatistics;
import com.R4B.api.dto.riot.Item;
import com.R4B.api.dto.riot.StaticChampion;
import com.R4B.api.dto.riot.StaticSummonerSpell;
import com.R4B.api.dto.status.BotStatus;
import com.R4B.api.dto.statistics.ProfileActivity;
import com.R4B.api.dto.statistics.ProfileMatchups;
import com.R4B.api.dto.statistics.ProfileStatistics;
import com.R4B.api.dto.summoner.Mastery;
import com.R4B.api.dto.summoner.Summoner;
import com.R4B.api.dto.summoner.SummonerView;
import com.R4B.client.R4B;
import com.R4B.client.core.AccessScope;
import com.R4B.client.core.EndpointDefinition;
import com.R4B.client.core.R4BRequestBuilder;
import com.R4B.client.exception.R4BException;
import com.R4B.client.exception.R4BPendingException;
import com.R4B.lol.GameQueueType;
import com.R4B.lol.LaneType;
import com.R4B.lol.LeagueShard;
import com.R4B.lol.MatchOrder;
import com.fasterxml.jackson.core.type.TypeReference;

public final class LolApiClient {

    private static final EndpointDefinition MASTERIES = new EndpointDefinition(
        "summoner.masteries", EndpointDefinition.HttpMethod.GET, Set.of(), AccessScope.PUBLIC, "summoner", false);
    private static final EndpointDefinition OWNER = new EndpointDefinition(
        "summoner.owner", EndpointDefinition.HttpMethod.GET, Set.of(), AccessScope.INTERNAL, "summoner", false);
    private static final EndpointDefinition TRACKING = new EndpointDefinition(
        "summoner.tracking", EndpointDefinition.HttpMethod.POST, Set.of("userId", "tracking"),
        AccessScope.INTERNAL, "summoner", true);
    private static final EndpointDefinition MATCHES_REFRESH = new EndpointDefinition(
        "summoner.matches.refresh", EndpointDefinition.HttpMethod.POST, Set.of("queue", "offset"),
        AccessScope.PUBLIC, "matches", true);
    private static final EndpointDefinition PROFILE_STATISTICS = new EndpointDefinition(
        "summoner.statistics", EndpointDefinition.HttpMethod.GET, Set.of("filter"), AccessScope.PUBLIC, "profile", false);
    private static final EndpointDefinition CHAMPION_STATISTICS = new EndpointDefinition(
        "champion.statistics", EndpointDefinition.HttpMethod.GET, Set.of("filter"), AccessScope.PUBLIC, "champion", false);
    private static final EndpointDefinition CHAMPION_BUILD = new EndpointDefinition(
        "champion.build", EndpointDefinition.HttpMethod.GET, Set.of("filter"), AccessScope.PUBLIC, "champion-build", false);
    private static final EndpointDefinition CHAMPION_STATISTICS_ALL = new EndpointDefinition(
        "champion.statistics.all", EndpointDefinition.HttpMethod.GET, Set.of("filter"), AccessScope.PUBLIC, "champion", false);
    private static final EndpointDefinition CHAMPION_STATISTICS_REFRESH = new EndpointDefinition(
        "champion.statistics.refresh", EndpointDefinition.HttpMethod.POST, Set.of("patch", "queue"),
        AccessScope.INTERNAL, "champion-refresh", true);
    private static final EndpointDefinition STATIC_DATA = new EndpointDefinition(
        "static.data", EndpointDefinition.HttpMethod.GET, Set.of(), AccessScope.PUBLIC, "static-data", false);

    private LolApiClient() {
    }

    public static SummonerView getProfile(String puuid, LeagueShard region) {
        return pendingAsNull(() -> request(() -> R4B.summoner.getProfile(puuid, region).sync()));
    }

    public static SummonerView getProfileByName(String name, String tag, LeagueShard region) {
        return pendingAsNull(() -> request(() -> R4B.summoner.getProfileByName(name, tag, region).sync()));
    }

    public static List<SummonerView> search(String query, LeagueShard region) {
        return request(() -> R4B.summoner.search(query, region).sync());
    }

    public static Map<Integer, StaticChampion> getChampions() {
        Type responseType = new TypeReference<Map<Integer, StaticChampion>>() {}.getType();
        return request(() -> new StaticDataBuilder<Map<Integer, StaticChampion>>(
            "/api/lol/static/champions", R4BRequestBuilder.type(responseType)).sync());
    }

    public static StaticChampion getChampion(int id) {
        return request(() -> new StaticDataBuilder<StaticChampion>(
            "/api/lol/static/champion/" + id, R4BRequestBuilder.type(StaticChampion.class)).sync());
    }

    public static Item getItem(int id) {
        return request(() -> new StaticDataBuilder<Item>(
            "/api/lol/static/item/" + id, R4BRequestBuilder.type(Item.class)).sync());
    }

    public static StaticSummonerSpell getSummonerSpell(int id) {
        return request(() -> new StaticDataBuilder<StaticSummonerSpell>(
            "/api/lol/static/summoner-spell/" + id, R4BRequestBuilder.type(StaticSummonerSpell.class)).sync());
    }

    public static String getRunes() {
        return request(() -> new StaticDataBuilder<String>(
            "/api/lol/static/runes", R4BRequestBuilder.type(String.class)).sync());
    }

    public static BotStatus getStatus() {
        return request(() -> R4B.status.get().sync());
    }

    public static LiveGame getLiveGame(String puuid, LeagueShard region) {
        return request(() -> R4B.summoner.liveGame(puuid, region).sync());
    }

    public static Match getMatch(String gameId, LeagueShard region) {
        return pendingAsNull(() -> request(() -> R4B.match.get(gameId, region).sync()));
    }

    public static void refreshProfile(String puuid, LeagueShard region) {
        R4B.summoner.refresh(puuid, region).sync();
    }

    public static void refreshMatches(String puuid, LeagueShard region, GameQueueType queue, int offset) {
        new VoidBuilder(
            MATCHES_REFRESH,
            shardPath(region) + "/profile/" + segment(puuid) + "/matches/refresh"
        ).withQueue(queue).withOffset(offset).sync();
    }

    public static ProfileRecordPage getRecords(String puuid, LeagueShard region) {
        return request(() -> R4B.summoner.records(puuid, region).sync());
    }

    public static RankHistory getRankHistory(String puuid, LeagueShard region, GameQueueType queue) {
        return request(() -> R4B.summoner.rankHistory(puuid, region).withQueue(queue).sync());
    }

    public static ProfileStatistics getProfileStatistics(String puuid, LeagueShard region, com.R4B.api.dto.Filter filter) {
        try {
            return request(() -> new FilteredBuilder<ProfileStatistics>(
                PROFILE_STATISTICS, shardPath(region) + "/profile/" + segment(puuid) + "/statistics",
                R4BRequestBuilder.type(ProfileStatistics.class)).withFilter(filter).sync());
        } catch (R4BPendingException ignored) {
            return null;
        }
    }

    public static List<ChampionStatistics> getChampionStatistics(com.R4B.api.dto.Filter filter) {
        Type responseType = new TypeReference<List<ChampionStatistics>>() {}.getType();
        List<ChampionStatistics> statistics = pendingAsNull(() -> request(() -> new FilteredBuilder<List<ChampionStatistics>>(
            CHAMPION_STATISTICS_ALL, "/api/lol/champions/statistics", R4BRequestBuilder.type(responseType))
            .withFilter(filter).sync()));
        return statistics == null ? List.of() : statistics;
    }

    public static ChampionStatistics getChampionStatistics(String champion, com.R4B.api.dto.Filter filter) {
        return pendingAsNull(() -> request(() -> new FilteredBuilder<ChampionStatistics>(
            CHAMPION_STATISTICS, "/api/lol/champion/" + segment(champion) + "/statistics",
            R4BRequestBuilder.type(ChampionStatistics.class)).withFilter(filter).sync()));
    }

    public static Build getChampionBuild(String champion, com.R4B.api.dto.Filter filter) {
        return pendingAsNull(() -> request(() -> new FilteredBuilder<Build>(
            CHAMPION_BUILD, "/api/lol/champion/" + segment(champion) + "/build",
            R4BRequestBuilder.type(Build.class)).withFilter(filter).sync()));
    }

    public static CompletableFuture<Map<String, Integer>> refreshChampionStatistics(String patch, GameQueueType queue) {
        Type responseType = new TypeReference<Map<String, Integer>>() {}.getType();
        return new ChampionMatrixBuilder(CHAMPION_STATISTICS_REFRESH, "/api/lol/champions/statistics/refresh", responseType)
            .withPatch(patch).withQueue(queue).async();
    }

    public static MatchPage getMatches(
            String puuid,
            LeagueShard region,
            GameQueueType queue,
            int limit,
            int offset,
            long timeStart,
            long timeEnd,
            MatchOrder sort,
            String champion,
            LaneType lane,
            String patch) {
        return pendingAsNull(() -> request(() -> R4B.summoner.matches(puuid, region)
            .withQueue(queue)
            .withLimit(limit)
            .withOffset(offset)
            .withTimeStart(timeStart)
            .withTimeEnd(timeEnd)
            .withSort(sort)
            .withChampion(champion)
            .withLane(lane)
            .withPatch(patch)
            .sync()));
    }

    public static ProfileActivity getActivity(
            String puuid,
            LeagueShard region,
            long start,
            long end,
            GameQueueType queue,
            int champion) {
        return request(() -> R4B.summoner.activity(puuid, region)
            .withStart(start)
            .withEnd(end)
            .withQueue(queue)
            .withChampion(champion)
            .sync());
    }

    public static ProfileMatchups getMatchups(
            String puuid,
            LeagueShard region,
            long start,
            long end,
            GameQueueType queue,
            String patch,
            LaneType role,
            int minGames) {
        return pendingAsNull(() -> request(() -> R4B.summoner.matchups(puuid, region)
            .withStart(start)
            .withEnd(end)
            .withQueue(queue)
            .withPatch(patch)
            .withRole(role)
            .withMinGames(minGames)
            .sync()));
    }

    public static List<Mastery> getMasteries(String puuid, LeagueShard region) {
        Type responseType = new TypeReference<List<Mastery>>() {}.getType();
        return request(() -> new MasteriesBuilder(
            MASTERIES,
            shardPath(region) + "/profile/" + segment(puuid) + "/masteries",
            R4BRequestBuilder.type(responseType)
        ).sync());
    }

    public static String getOwner(String puuid, LeagueShard region) {
        Type responseType = new TypeReference<Map<String, String>>() {}.getType();
        Map<String, String> response = request(() -> new R4BRequestBuilder<Map<String, String>>(
            OWNER,
            shardPath(region) + "/profile/" + segment(puuid) + "/owner",
            R4BRequestBuilder.type(responseType)
        ) {}.sync());
        return response == null ? null : response.get("userId");
    }

    public static boolean setTracking(String puuid, LeagueShard region, String userId, boolean tracking) {
        try {
            new TrackingBuilder(
                TRACKING,
                shardPath(region) + "/profile/" + segment(puuid) + "/tracking"
            ).withUserId(userId).withTracking(tracking).sync();
            return true;
        } catch (R4BException exception) {
            if (exception.statusCode() == 404) return false;
            throw exception;
        }
    }

    private static <T> T request(Supplier<T> request) {
        try {
            return request.get();
        } catch (R4BException exception) {
            if (exception.statusCode() == 404) return null;
            throw exception;
        }
    }

    private static <T> T pendingAsNull(Supplier<T> request) {
        try {
            return request.get();
        } catch (R4BPendingException ignored) {
            return null;
        }
    }

    private static String shardPath(LeagueShard region) {
        if (region == null) throw new IllegalArgumentException("Region is required");
        return "/api/lol/" + region.name();
    }

    private static String segment(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Path value is required");
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static final class MasteriesBuilder extends R4BRequestBuilder<List<Mastery>> {
        private MasteriesBuilder(EndpointDefinition endpoint, String path, com.fasterxml.jackson.databind.JavaType type) {
            super(endpoint, path, type);
        }
    }

    private static final class TrackingBuilder extends R4BRequestBuilder<Void> {
        private TrackingBuilder(EndpointDefinition endpoint, String path) {
            super(endpoint, path, R4BRequestBuilder.type(Void.class));
        }

        private TrackingBuilder withUserId(String value) {
            query("userId", value);
            return this;
        }

        private TrackingBuilder withTracking(boolean value) {
            query("tracking", value);
            return this;
        }
    }

    private static final class VoidBuilder extends R4BRequestBuilder<Void> {
        private VoidBuilder(EndpointDefinition endpoint, String path) {
            super(endpoint, path, R4BRequestBuilder.type(Void.class));
        }

        private VoidBuilder withQueue(GameQueueType value) {
            query("queue", value);
            return this;
        }

        private VoidBuilder withOffset(int value) {
            query("offset", value);
            return this;
        }
    }

    private static final class FilteredBuilder<T> extends R4BRequestBuilder<T> {
        private FilteredBuilder(EndpointDefinition endpoint, String path, com.fasterxml.jackson.databind.JavaType type) {
            super(endpoint, path, type);
        }

        private FilteredBuilder<T> withFilter(com.R4B.api.dto.Filter filter) {
            if (filter == null) throw new IllegalArgumentException("Filter is required");
            query("filter", filter.toStateKey());
            return this;
        }
    }

    private static final class ChampionMatrixBuilder extends R4BRequestBuilder<Map<String, Integer>> {
        private ChampionMatrixBuilder(EndpointDefinition endpoint, String path, Type responseType) {
            super(endpoint, path, R4BRequestBuilder.type(responseType));
        }

        private ChampionMatrixBuilder withPatch(String value) {
            query("patch", value);
            return this;
        }

        private ChampionMatrixBuilder withQueue(GameQueueType value) {
            query("queue", value);
            return this;
        }
    }

    private static final class StaticDataBuilder<T> extends R4BRequestBuilder<T> {
        private StaticDataBuilder(String path, com.fasterxml.jackson.databind.JavaType type) {
            super(STATIC_DATA, path, type);
        }
    }
}
