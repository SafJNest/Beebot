package com.safjnest.lol.service;

import com.safjnest.r4j.RiotGateway;

import static com.safjnest.utils.ValidationUtils.valid;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.safjnest.lol.queue.QueueHandler;
import com.safjnest.r4j.scheduler.RiotScheduler;
import com.R4B.api.dto.match.LiveGame;
import com.R4B.api.dto.riot.RiotAccount;
import com.R4B.api.dto.riot.SpectatorGameInfo;
import com.R4B.api.dto.statistics.ProfileStatistics;
import com.R4B.api.dto.summoner.Mastery;
import com.R4B.api.dto.summoner.Rank;
import com.R4B.api.dto.summoner.Summoner;
import com.R4B.api.dto.summoner.SummonerView;
import com.R4B.lol.*;
import com.safjnest.nosql.MongoDB;
import com.safjnest.redis.RedisClient;
import com.safjnest.redis.RedisKey;

import net.dv8tion.jda.api.interactions.commands.Command.Choice;

public final class SummonerService {

    private static final TypeReference<List<SummonerView>> SUMMONER_SEARCH_TYPE =
        new TypeReference<List<SummonerView>>() {};
    private static final TypeReference<List<SummonerAutocompleteChoice>> SUMMONER_AUTOCOMPLETE_TYPE =
        new TypeReference<>() {};

    private SummonerService() {
    }

    public record RefreshResult(
        com.R4B.api.dto.summoner.Summoner summoner,
        boolean refreshed
    ) {
        private static RefreshResult ignored() {
            return new RefreshResult(null, false);
        }
    }

    public static Summoner find(String puuid, LeagueShard shard) {
        if (!valid(puuid, shard)) return null;

        Summoner cached = cache(puuid, shard);
        if (cached != null) return cached;

        Summoner stored = query(puuid, shard);
        if (stored != null) RedisClient.set(RedisKey.SUMMONER, stored, LeagueShard.cacheRegion(shard), shard.name(), puuid);
        return stored;
    }

    public static CompletableFuture<Summoner> getAsync(String puuid, LeagueShard shard) {
        return QueueHandler.thenCompose(getSummonerAsync(puuid, shard), SummonerService::hydrate);
    }

    public static Summoner get(String puuid, LeagueShard shard) {
        try {
            return getAsync(puuid, shard).join();
        } catch (CompletionException exception) {
            return null;
        }
    }

    public static RefreshResult refresh(String puuid, LeagueShard shard) {
        try {
            return refreshAsync(puuid, shard).join();
        } catch (CompletionException exception) {
            return RefreshResult.ignored();
        }
    }

    public static CompletableFuture<RefreshResult> refreshAsync(String puuid, LeagueShard shard) {
        if (!valid(puuid, shard)
                || !RedisClient.claim(RedisKey.R4J_SUMMONER_REFRESH_COOLDOWN, "1", shard.name(), puuid)) {
            return CompletableFuture.completedFuture(RefreshResult.ignored());
        }

        invalidateRefreshSourceCaches(puuid, shard);
        return QueueHandler.thenCompose(refreshRiotAccountAsync(puuid, shard), account ->
            QueueHandler.thenCompose(refreshRiotSummonerAsync(puuid, shard), source -> refreshProfile(source, account)))
            .exceptionally(exception -> {
                System.err.println("Summoner refresh failed puuid=" + puuid + " shard=" + shard + " " + exception.getMessage());
                return RefreshResult.ignored();
            });
    }

    public static com.R4B.api.dto.summoner.Summoner getRiotSummoner(String puuid, LeagueShard shard) {
        if (!valid(puuid, shard)) return null;

        com.R4B.api.dto.summoner.Summoner cached = cacheRiotSummoner(puuid, shard);
        if (cached != null) return cached;

        try {
            return getRiotSummonerAsync(puuid, shard).join();
        } catch (CompletionException exception) {
            return null;
        }
    }

    public static com.R4B.api.dto.summoner.Summoner getRiotSummoner(
            String name,
            String tag,
            LeagueShard shard) {
        String puuid = getPuuidByRiotId(name, tag, shard);
        return puuid == null ? null : getRiotSummoner(puuid, shard);
    }

    public static CompletableFuture<com.R4B.api.dto.summoner.Summoner> getRiotSummonerAsync(
            String puuid,
            LeagueShard shard) {
        if (!valid(puuid, shard)) return CompletableFuture.completedFuture(null);

        com.R4B.api.dto.summoner.Summoner cached = cacheRiotSummoner(puuid, shard);
        if (cached != null) return saveAsync(cached).thenApply(ignored -> cached);

        return QueueHandler.thenCompose(QueueHandler.immediate(RiotScheduler.class, shard, shard.name() + ":summoner:" + puuid,
            "summoner puuid=" + puuid, com.R4B.api.dto.summoner.Summoner.class, ignored -> {
            com.R4B.api.dto.summoner.Summoner summoner =
                RiotGateway.summoner(shard, puuid);
            return summoner;
        }), summoner -> summoner == null
            ? CompletableFuture.completedFuture(null)
            : saveAsync(summoner).thenApply(ignored -> summoner));
    }

    public static boolean upsert(Summoner summoner, String userId) {
        if (summoner == null || summoner.puuid() == null || summoner.region() == null) return false;
        if (summoner.riotId() == null || summoner.riotId().isBlank()) {
            RiotAccount account = getRiotAccount(summoner.puuid(), summoner.region());
            if (account != null) summoner.setRiotId(account.getName() + "#" + account.getTag());
        }
        return MongoDB.upsertSummoner(summoner, userId);
    }

    public static String getUserId(String puuid, LeagueShard shard) {
        if (!valid(puuid, shard)) return null;

        String userId = RedisClient.get(RedisKey.R4J_USER_ID_BY_PUUID.of(shard.name(), puuid), String.class);
        if (userId != null) return userId;

        userId = MongoDB.findUserIdByPuuid(puuid, shard);
        if (userId != null) RedisClient.set(RedisKey.R4J_USER_ID_BY_PUUID, userId, shard.name(), puuid);
        return userId;
    }

    public static RiotAccount getRiotAccount(String puuid, LeagueShard shard) {
        try {
            return getRiotAccountAsync(puuid, shard).join();
        } catch (CompletionException exception) {
            return null;
        }
    }

    public static RiotAccount getRiotAccount(String name, String tag, LeagueShard shard) {
        try {
            return getRiotAccountAsync(name, tag, shard).join();
        } catch (CompletionException exception) {
            return null;
        }
    }

    public static CompletableFuture<RiotAccount> getRiotAccountAsync(String puuid, LeagueShard shard) {
        if (!valid(puuid, shard)) return CompletableFuture.completedFuture(null);

        RiotAccount cached = RedisClient.get(RedisKey.R4J_ACCOUNT.of(shard.name(), puuid), RiotAccount.class);
        if (cached != null) return CompletableFuture.completedFuture(cached);

        return QueueHandler.immediate(RiotScheduler.class, shard, shard.name() + ":account:" + puuid,
            "account puuid=" + puuid, RiotAccount.class, ignored -> {
            RiotAccount account = RiotGateway.account(
                com.R4B.lol.LeagueShard.getAccountRegion(shard), puuid);
            if (account != null) RedisClient.set(RedisKey.R4J_ACCOUNT, account, shard.name(), puuid);
            return account;
        });
    }

    public static CompletableFuture<RiotAccount> getRiotAccountAsync(String name, String tag, LeagueShard shard) {
        if (!valid(name, tag, shard)) return CompletableFuture.completedFuture(null);

        RiotAccount cached = RedisClient.get(RedisKey.R4J_ACCOUNT_BY_NAME.of(shard.name(), name, tag), RiotAccount.class);
        if (cached != null) return CompletableFuture.completedFuture(cached);

        String riotId = name + "#" + tag;
        return QueueHandler.immediate(RiotScheduler.class, shard, shard.name() + ":account:" + riotId,
            "account riotId=" + riotId, RiotAccount.class, ignored -> {
            RiotAccount account = RiotGateway.account(
                com.R4B.lol.LeagueShard.getAccountRegion(shard), name, tag);
            if (account != null) {
                RedisClient.set(RedisKey.R4J_ACCOUNT_BY_NAME, account, shard.name(), name, tag);
                RedisClient.set(RedisKey.R4J_ACCOUNT, account, shard.name(), account.getPUUID());
            }
            return account;
        });
    }

    public static String getPuuidByRiotId(String name, String tag, LeagueShard shard) {
        String stored = queryPuuid(name, tag, shard);
        if (stored != null) return stored;

        RiotAccount account = getRiotAccount(name, tag, shard);
        return account == null ? null : account.getPUUID();
    }

    public static CompletableFuture<String> getPuuidByRiotIdAsync(String name, String tag, LeagueShard shard) {
        String stored = queryPuuid(name, tag, shard);
        if (stored != null) return CompletableFuture.completedFuture(stored);

        return getRiotAccountAsync(name, tag, shard).thenApply(account -> account == null ? null : account.getPUUID());
    }

    public static RiotAccount getRiotAccountFromSummoner(com.R4B.api.dto.summoner.Summoner summoner) {
        return summoner == null ? null : getRiotAccount(summoner.getPUUID(), summoner.getPlatform());
    }

    public static LiveGame getLiveGame(String puuid, LeagueShard shard) {
        SpectatorGameInfo game = getSpectatorGame(puuid, shard);
        if (game == null && get(puuid, shard) == null) return null;
        Map<String, LiveGame.ProfileOverview> profiles = profileOverviews(game, shard);
        queueSpectatorSummoners(game, shard);
        return LiveGameProjection.fromR4J(game, profiles);
    }

    public static LiveGame getLiveGame(String name, String tag, LeagueShard shard) {
        String puuid = getPuuidByRiotId(name, tag, shard);
        return puuid == null ? null : getLiveGame(puuid, shard);
    }

    public static SpectatorGameInfo getSpectatorGame(String puuid, LeagueShard shard) {
        if (!valid(puuid, shard)) return null;

        SpectatorGameInfo cached = RedisClient.get(RedisKey.R4J_SPECTATOR_CURRENT.of(shard.name(), puuid), SpectatorGameInfo.class);
        if (cached != null) return cached;

        try {
            return QueueHandler.immediate(RiotScheduler.class, shard, shard.name() + ":spectator:" + puuid,
                "spectator puuid=" + puuid, SpectatorGameInfo.class, ignored -> {
                SpectatorGameInfo game = RiotGateway.spectator(shard, puuid);
                if (game != null) RedisClient.set(RedisKey.R4J_SPECTATOR_CURRENT, game, shard.name(), puuid);
                return game;
            }).join();
        } catch (CompletionException exception) {
            return null;
        }
    }

    public static List<SummonerView> search(String query, LeagueShard shard) {
        String normalizedQuery = normalizeSearch(query);
        String key = RedisKey.SUMMONER_SEARCH.of(LeagueShard.cacheRegion(shard), shard.name(), normalizedQuery);
        List<SummonerView> cached = RedisClient.get(key, SUMMONER_SEARCH_TYPE);
        if (cached != null) return cached;

        List<MongoDB.SummonerSearchResult> rows = new ArrayList<>(querySearch(normalizedQuery, shard));
        sortSearchResults(rows);

        List<SummonerView> summoners = new ArrayList<>();
        for (MongoDB.SummonerSearchResult row : rows) {
            Rank rank = row.soloRank() != null ? row.soloRank() : Rank.unranked();
            summoners.add(SummonerProjection.view(row.summoner(), Map.of(GameQueueType.RANKED_SOLO_5X5, rank), new ProfileStatistics(), List.of()));
        }
        RedisClient.set(RedisKey.SUMMONER_SEARCH, summoners, LeagueShard.cacheRegion(shard), shard.name(), normalizedQuery);
        return summoners;
    }

    public static List<Choice> autocomplete(String query, LeagueShard shard) {
        if (query == null || query.isBlank()) return new ArrayList<>();

        String normalizedQuery = normalizeSearch(query);
        if (normalizedQuery.isEmpty()) return new ArrayList<>();

        String key = RedisKey.SUMMONER_AUTOCOMPLETE.of(LeagueShard.cacheRegion(shard), shard.name(), normalizedQuery);
        List<SummonerAutocompleteChoice> cached = RedisClient.get(key, SUMMONER_AUTOCOMPLETE_TYPE);
        if (cached != null) return toChoices(cached);

        List<SummonerAutocompleteChoice> choices = new ArrayList<>();
        for (MongoDB.SummonerSearchResult row : querySearch(normalizedQuery, shard)) {
            choices.add(new SummonerAutocompleteChoice(row.summoner().riotId(), row.summoner().puuid()));
        }
        RedisClient.set(RedisKey.SUMMONER_AUTOCOMPLETE, choices, LeagueShard.cacheRegion(shard), shard.name(), normalizedQuery);
        return toChoices(choices);
    }

    public static String normalizeSearch(String query) {
        if (query == null) return "";

        String lowerCaseQuery = query.toLowerCase(Locale.ROOT);
        StringBuilder normalizedQuery = new StringBuilder();
        for (int index = 0; index < lowerCaseQuery.length(); index++) {
            char character = lowerCaseQuery.charAt(index);
            if (!Character.isWhitespace(character) && character != '-' && character != '#') normalizedQuery.append(character);
        }
        return normalizedQuery.toString();
    }

    public static void invalidate(String puuid, LeagueShard shard) {
        if (!valid(puuid, shard)) return;

        CacheInvalidationService.clearRedis(List.of(
            RedisKey.R4J_SUMMONER.of(shard.name(), puuid),
            RedisKey.R4J_ACCOUNT.of(shard.name(), puuid),
            RedisKey.R4J_USER_ID_BY_PUUID.of(shard.name(), puuid),
            RedisKey.R4J_LEAGUE_ENTRIES.of(shard.name(), puuid),
            RedisKey.R4J_CHAMPION_MASTERIES.of(shard.name(), puuid),
            RedisKey.SUMMONER_RANK.of(LeagueShard.cacheRegion(shard), shard.name(), puuid),
            RedisKey.SUMMONER_RANKS.of(LeagueShard.cacheRegion(shard), shard.name(), puuid),
            RedisKey.SUMMONER_MASTERIES.of(LeagueShard.cacheRegion(shard), shard.name(), puuid),
            RedisKey.R4J_SPECTATOR_CURRENT.of(shard.name(), puuid),
            RedisKey.R4J_MATCH_LIST.of(shard.name(), puuid, "null", 0),
            RedisKey.SUMMONER.of(LeagueShard.cacheRegion(shard), shard.name(), puuid)
        ));
        ProfileService.invalidate(puuid, shard);
    }

    // ============================================================================

    private record SummonerAutocompleteChoice(String riotId, String puuid) {}

    static CompletableFuture<Summoner> getSummonerAsync(String puuid, LeagueShard shard) {
        Summoner saved = find(puuid, shard);
        return saved != null
            ? CompletableFuture.completedFuture(saved)
            : getRiotSummonerAsync(puuid, shard).thenApply(ignored -> find(puuid, shard));
    }

    private static Summoner cache(String puuid, LeagueShard shard) {
        return RedisClient.get(RedisKey.SUMMONER.of(LeagueShard.cacheRegion(shard), shard.name(), puuid), Summoner.class);
    }

    private static com.R4B.api.dto.summoner.Summoner cacheRiotSummoner(String puuid, LeagueShard shard) {
        return RiotGateway.cachedSummoner(shard, puuid);
    }

    private static Summoner query(String puuid, LeagueShard shard) {
        return MongoDB.findSummoner(puuid, shard);
    }

    private static String queryPuuid(String name, String tag, LeagueShard shard) {
        if (!valid(name, tag, shard)) return null;
        return MongoDB.findPuuid(name + "#" + tag, shard);
    }

    private static List<MongoDB.SummonerSearchResult> querySearch(String normalizedQuery, LeagueShard shard) {
        return MongoDB.findSummonerSearch(normalizedQuery, shard, 25);
    }

    static void sortSearchResults(List<MongoDB.SummonerSearchResult> rows) {
        rows.sort(Comparator
            .comparingInt(SummonerService::searchMmr)
            .reversed()
            .thenComparing(row -> row.summoner().riotId(), Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
            .thenComparing(row -> row.summoner().puuid()));
    }

    private static int searchMmr(MongoDB.SummonerSearchResult row) {
        Rank rank = row.soloRank();
        return rank == null ? -1 : TierDivisionType.getMmr(rank.tier(), rank.lp());
    }

    private static CompletableFuture<RiotAccount> refreshRiotAccountAsync(String puuid, LeagueShard shard) {
        return QueueHandler.immediate(RiotScheduler.class, shard, shard.name() + ":account-refresh:" + puuid,
            "account refresh puuid=" + puuid, RiotAccount.class, ignored -> {
            RiotAccount account = RiotGateway.account(
                com.R4B.lol.LeagueShard.getAccountRegion(shard), puuid);
            if (account != null) RedisClient.set(RedisKey.R4J_ACCOUNT, account, shard.name(), puuid);
            return account;
        });
    }

    private static CompletableFuture<com.R4B.api.dto.summoner.Summoner> refreshRiotSummonerAsync(
        String puuid,
        LeagueShard shard
    ) {
        return QueueHandler.immediate(RiotScheduler.class, shard, shard.name() + ":summoner-refresh:" + puuid,
            "summoner refresh puuid=" + puuid,com.R4B.api.dto.summoner.Summoner.class,  ignored -> {
            com.R4B.api.dto.summoner.Summoner summoner =
                RiotGateway.summoner(shard, puuid);
            return summoner;
        });
    }

    private static CompletableFuture<RefreshResult> refreshProfile(
        com.R4B.api.dto.summoner.Summoner source,
        RiotAccount account
    ) {
        if (source == null || source.getPUUID() == null || source.getPlatform() == null) {
            return CompletableFuture.completedFuture(RefreshResult.ignored());
        }

        String riotId = account == null ? null : account.getName() + "#" + account.getTag();
        Summoner profile = store(persist(source, riotId, null), source.getPlatform());
        if (profile == null) return CompletableFuture.completedFuture(RefreshResult.ignored());

        LeagueShard sourceShard = source.getPlatform();
        return QueueHandler.thenApply(QueueHandler.thenCompose(RankService.refreshAsync(source.getPUUID(), sourceShard),
            ignored -> MasteryService.refreshAsync(source.getPUUID(), sourceShard)), ignored -> {
                ProfileService.refreshProfile(profile, sourceShard);
                return new RefreshResult(source, true);
            });
    }

    private static void invalidateRefreshSourceCaches(String puuid, LeagueShard shard) {
        CacheInvalidationService.clearRedis(List.of(
            RedisKey.R4J_SUMMONER.of(shard.name(), puuid),
            RedisKey.R4J_ACCOUNT.of(shard.name(), puuid),
            RedisKey.R4J_LEAGUE_ENTRIES.of(shard.name(), puuid),
            RedisKey.R4J_CHAMPION_MASTERIES.of(shard.name(), puuid),
            RedisKey.R4J_SPECTATOR_CURRENT.of(shard.name(), puuid),
            RedisKey.R4J_MATCH_LIST.of(shard.name(), puuid, "null", 0),
            RedisKey.SUMMONER.of(LeagueShard.cacheRegion(shard), shard.name(), puuid),
            RedisKey.SUMMONER_RANK.of(LeagueShard.cacheRegion(shard), shard.name(), puuid),
            RedisKey.SUMMONER_RANKS.of(LeagueShard.cacheRegion(shard), shard.name(), puuid),
            RedisKey.SUMMONER_MASTERIES.of(LeagueShard.cacheRegion(shard), shard.name(), puuid)
        ));
        ProfileService.invalidate(puuid, shard);
    }

    private static Map<String, LiveGame.ProfileOverview> profileOverviews(
        SpectatorGameInfo game,
        LeagueShard shard
    ) {
        if (game == null || game.getParticipants() == null || game.getParticipants().isEmpty()) return Map.of();

        Map<String, Integer> championIds = new LinkedHashMap<>();
        for (var participant : game.getParticipants()) {
            if (participant != null && participant.getPuuid() != null && !participant.getPuuid().isBlank())
                championIds.put(participant.getPuuid(), participant.getChampionId());
        }
        if (championIds.isEmpty()) return Map.of();

        ProfileService profileService = new ProfileService();
        Map<String, LiveGame.ProfileOverview> result = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : championIds.entrySet()) {
            String puuid = entry.getKey();
            Summoner summoner = find(puuid, shard);
            if (summoner == null) continue;

            Map<GameQueueType, com.R4B.api.dto.summoner.Rank> ranks = RankService.find(puuid, shard);
            List<Mastery> masteries = MasteryService.find(puuid, shard);
            Map<Integer, com.R4B.api.dto.statistics.shared.ProfileLeafStats> championStats = championStats(
                profileService.getStatistics(summoner, shard, FilterDefaults.canonical()), entry.getValue());
            result.put(puuid, new LiveGame.ProfileOverview(summoner, ranks, masteries, championStats));
        }
        return result;
    }

    private static Map<Integer, com.R4B.api.dto.statistics.shared.ProfileLeafStats> championStats(ProfileStatistics statistics, Integer championId) {
        if (statistics == null) return Map.of();
        Map<Integer, com.R4B.api.dto.statistics.shared.ProfileLeafStats> available = ProfileStatisticsAccumulator.of(statistics).championStats();
        List<Integer> ids = new ArrayList<>(available.keySet());
        ids.sort(Comparator.comparingLong((Integer id) -> available.get(id).games).reversed().thenComparingInt(Integer::intValue));
        Map<Integer, com.R4B.api.dto.statistics.shared.ProfileLeafStats> result = new LinkedHashMap<>();
        if (championId != null && available.containsKey(championId)) result.put(championId, available.get(championId));
        for (Integer id : ids) {
            if (result.size() == 3) break;
            result.putIfAbsent(id, available.get(id));
        }
        return Map.copyOf(result);
    }

    private static void queueSpectatorSummoners(SpectatorGameInfo game, LeagueShard shard) {
        if (game == null || shard == null || game.getParticipants() == null || game.getParticipants().isEmpty()) return;

        for (var participant : game.getParticipants()) {
            if (participant == null || participant.getPuuid() == null || participant.getPuuid().isBlank()) continue;
            if (!MongoDB.upsertSummoner(participant, shard)) continue;
            RankService.refreshBackgroundAsync(participant.getPuuid(), shard);
        }
    }

    private static CompletableFuture<Summoner> hydrate(Summoner identity) {
        if (identity == null || identity.puuid() == null || identity.puuid().isBlank() || identity.region() == null)
            return CompletableFuture.completedFuture(null);
        String puuid = identity.puuid();
        LeagueShard shard = identity.region();
        Map<GameQueueType, Rank> foundRanks = RankService.find(puuid, shard);
        List<Mastery> foundMasteries = MasteryService.find(puuid, shard);
        CompletableFuture<Map<GameQueueType, Rank>> ranksFuture = foundRanks != null
            ? CompletableFuture.completedFuture(foundRanks)
            : RankService.getAsync(puuid, shard);
        return QueueHandler.thenCompose(ranksFuture, ranks -> {
            if (foundRanks == null && ranks != null) {
                for (Map.Entry<GameQueueType, Rank> entry : ranks.entrySet()) {
                    if (entry.getKey() != null && entry.getValue() != null) identity.setRank(entry.getKey(), entry.getValue());
                }
            }
            CompletableFuture<List<Mastery>> masteriesFuture = foundMasteries != null
                ? CompletableFuture.completedFuture(foundMasteries)
                : MasteryService.getAsync(puuid, shard);
            return QueueHandler.thenApply(masteriesFuture, masteries -> {
                if (foundMasteries == null && masteries != null) identity.setMasteries(masteries);
                RedisClient.set(RedisKey.SUMMONER, identity, LeagueShard.cacheRegion(shard), shard.name(), puuid);
                return identity;
            });
        }).exceptionally(ignored -> identity);
    }

    private static CompletableFuture<Summoner> saveAsync(com.R4B.api.dto.summoner.Summoner source) {
        String riotId = MongoDB.findSummonerName(source.getPUUID(), source.getPlatform());
        if (riotId != null && !riotId.isBlank()) return CompletableFuture.completedFuture(store(
            persist(source, riotId, null), source.getPlatform()));

        return QueueHandler.thenApply(getRiotAccountAsync(source.getPUUID(), source.getPlatform()), account -> store(
            persist(source, account == null ? null : account.getName() + "#" + account.getTag(), null),
            source.getPlatform()
        ));
    }

    private static Summoner persist(
            com.R4B.api.dto.summoner.Summoner source,
            String riotId,
            String userId) {
        Summoner result = new Summoner(
            source.getPUUID(),
            riotId,
            source.getPlatform(),
            source.getSummonerLevel(),
            source.getProfileIconId()
        );
        return MongoDB.upsertSummoner(result, userId) ? result : null;
    }

    private static Summoner store(Summoner summoner, LeagueShard shard) {
        if (summoner != null) RedisClient.set(RedisKey.SUMMONER, summoner, LeagueShard.cacheRegion(shard), shard.name(), summoner.puuid());
        return summoner;
    }

    private static List<Choice> toChoices(List<SummonerAutocompleteChoice> autocompleteChoices) {
        List<Choice> choices = new ArrayList<>();
        for (SummonerAutocompleteChoice choice : autocompleteChoices) choices.add(new Choice(choice.riotId(), choice.puuid()));
        return choices;
    }
}
