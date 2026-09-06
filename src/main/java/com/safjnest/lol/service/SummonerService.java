package com.safjnest.lol.service;

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
import com.safjnest.lol.LeagueHandler;
import com.safjnest.lol.model.Filter;
import com.safjnest.lol.model.match.LiveGame;
import com.safjnest.lol.queue.QueueHandler;
import com.safjnest.lol.queue.scheduler.RiotScheduler;
import com.safjnest.lol.model.statistics.ProfileStatistics;

import com.safjnest.lol.model.summoner.Mastery;
import com.safjnest.lol.model.summoner.Summoner;
import com.safjnest.lol.model.summoner.SummonerView;
import com.safjnest.lol.utils.LeagueShardUtils;
import com.safjnest.nosql.MongoDB;
import com.safjnest.redis.RedisClient;
import com.safjnest.redis.RedisKey;

import net.dv8tion.jda.api.interactions.commands.Command.Choice;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.pojo.lol.spectator.SpectatorGameInfo;
import no.stelar7.api.r4j.pojo.shared.RiotAccount;

public final class SummonerService {

    private record SummonerAutocompleteChoice(String riotId, String puuid) {}

    public record RefreshResult(
        no.stelar7.api.r4j.pojo.lol.summoner.Summoner summoner,
        boolean refreshed
    ) {
        private static RefreshResult ignored() {
            return new RefreshResult(null, false);
        }
    }

    private static final TypeReference<List<SummonerView>> SUMMONER_SEARCH_TYPE =
        new TypeReference<List<SummonerView>>() {};
    private static final TypeReference<List<SummonerAutocompleteChoice>> SUMMONER_AUTOCOMPLETE_TYPE =
        new TypeReference<>() {};

    private static final no.stelar7.api.r4j.impl.R4J RIOT_API = LeagueHandler.getRiotApi();

    private SummonerService() {
    }

    public static Summoner find(String puuid, LeagueShard shard) {
        if (!valid(puuid, shard)) return null;

        Summoner cached = cache(puuid, shard);
        if (cached != null) return cached;

        Summoner stored = query(puuid, shard);
        if (stored != null) RedisClient.set(RedisKey.SUMMONER, stored, LeagueShardUtils.cacheRegion(shard), shard.name(), puuid);
        return stored;
    }

    public static CompletableFuture<Summoner> getAsync(String puuid, LeagueShard shard) {
        Summoner saved = find(puuid, shard);
        return saved != null
            ? CompletableFuture.completedFuture(saved)
            : getRiotSummonerAsync(puuid, shard).thenApply(ignored -> find(puuid, shard));
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

        LeagueHandler.clearRiotProfileRefreshCache(shard, puuid);
        invalidateRefreshSourceCaches(puuid, shard);
        return refreshRiotAccountAsync(puuid, shard).thenCompose(account -> refreshRiotSummonerAsync(puuid, shard)
            .thenComposeAsync(source -> refreshProfile(source, account)))
            .exceptionally(ignored -> RefreshResult.ignored());
    }

    public static no.stelar7.api.r4j.pojo.lol.summoner.Summoner getRiotSummoner(String puuid, LeagueShard shard) {
        if (!valid(puuid, shard)) return null;

        no.stelar7.api.r4j.pojo.lol.summoner.Summoner cached = cacheRiotSummoner(puuid, shard);
        if (cached != null) return cached;

        try {
            return getRiotSummonerAsync(puuid, shard).join();
        } catch (CompletionException exception) {
            return null;
        }
    }

    public static no.stelar7.api.r4j.pojo.lol.summoner.Summoner getRiotSummoner(
            String name,
            String tag,
            LeagueShard shard) {
        String puuid = getPuuidByRiotId(name, tag, shard);
        return puuid == null ? null : getRiotSummoner(puuid, shard);
    }

    public static CompletableFuture<no.stelar7.api.r4j.pojo.lol.summoner.Summoner> getRiotSummonerAsync(
            String puuid,
            LeagueShard shard) {
        if (!valid(puuid, shard)) return CompletableFuture.completedFuture(null);

        no.stelar7.api.r4j.pojo.lol.summoner.Summoner cached = cacheRiotSummoner(puuid, shard);
        if (cached != null) return saveAsync(cached).thenApply(ignored -> cached);

        return QueueHandler.<no.stelar7.api.r4j.pojo.lol.summoner.Summoner>immediate(RiotScheduler.class, shard, shard.name() + ":summoner:" + puuid,
            "summoner puuid=" + puuid, ignored -> {
            no.stelar7.api.r4j.pojo.lol.summoner.Summoner summoner =
                RIOT_API.getLoLAPI().getSummonerAPI().getSummonerByPUUID(shard, puuid);
            if (summoner != null) RedisClient.set(RedisKey.R4J_SUMMONER, summoner, shard.name(), puuid);
            return summoner;
        }).thenComposeAsync(summoner -> summoner == null
            ? CompletableFuture.completedFuture(null)
            : saveAsync(summoner).thenApply(ignored -> summoner));
    }

    public static boolean upsert(Summoner summoner, String userId) {
        if (summoner == null || summoner.puuid() == null || summoner.region() == null) return false;
        return MongoDB.upsertSummoner(summoner, userId);
    }

    public static boolean upsert(no.stelar7.api.r4j.pojo.lol.summoner.Summoner summoner, String userId) {
        if (summoner == null || summoner.getPUUID() == null || summoner.getPlatform() == null) return false;

        String riotId = MongoDB.findSummonerName(summoner.getPUUID(), summoner.getPlatform());
        if (riotId == null || riotId.isBlank()) {
            RiotAccount account = getRiotAccount(summoner.getPUUID(), summoner.getPlatform());
            if (account != null) riotId = account.getName() + "#" + account.getTag();
        }
        return persist(summoner, riotId, userId) != null;
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
            "account puuid=" + puuid, ignored -> {
            RiotAccount account = RIOT_API.getAccountAPI().getAccountByPUUID(
                com.safjnest.lol.utils.LeagueShardUtils.getAccountRegion(shard), puuid);
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
            "account riotId=" + riotId, ignored -> {
            RiotAccount account = RIOT_API.getAccountAPI().getAccountByTag(
                com.safjnest.lol.utils.LeagueShardUtils.getAccountRegion(shard), name, tag);
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

    public static RiotAccount getRiotAccountFromSummoner(no.stelar7.api.r4j.pojo.lol.summoner.Summoner summoner) {
        return summoner == null ? null : getRiotAccount(summoner.getPUUID(), summoner.getPlatform());
    }

    public static LiveGame getLiveGame(String puuid, LeagueShard shard) {
        SpectatorGameInfo game = getSpectatorGame(puuid, shard);
        if (game == null && get(puuid, shard) == null) return null;
        Map<String, LiveGame.ProfileOverview> profiles = profileOverviews(game, shard);
        queueSpectatorSummoners(game, shard);
        return LiveGame.fromR4J(game, profiles);
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
            return QueueHandler.<SpectatorGameInfo>immediate(RiotScheduler.class, shard, shard.name() + ":spectator:" + puuid,
                "spectator puuid=" + puuid, ignored -> {
                SpectatorGameInfo game = RIOT_API.getLoLAPI().getSpectatorAPI().getCurrentGame(shard, puuid);
                if (game != null) RedisClient.set(RedisKey.R4J_SPECTATOR_CURRENT, game, shard.name(), puuid);
                return game;
            }).join();
        } catch (CompletionException exception) {
            return null;
        }
    }

    public static List<SummonerView> search(String query, LeagueShard shard) {
        String normalizedQuery = normalizeSearch(query);
        String key = RedisKey.SUMMONER_SEARCH.of(LeagueShardUtils.cacheRegion(shard), shard.name(), normalizedQuery);
        List<SummonerView> cached = RedisClient.get(key, SUMMONER_SEARCH_TYPE);
        if (cached != null) return cached;

        List<SummonerView> summoners = new ArrayList<>();
        for (MongoDB.SummonerSearchResult row : querySearch(normalizedQuery, shard)) {
            com.safjnest.lol.model.summoner.Rank rank = row.soloRank() != null
                ? row.soloRank()
                : com.safjnest.lol.model.summoner.Rank.unranked();
            summoners.add(SummonerView.from(row.summoner(), Map.of(GameQueueType.RANKED_SOLO_5X5, rank), new ProfileStatistics(), List.of()));
        }
        RedisClient.set(RedisKey.SUMMONER_SEARCH, summoners, LeagueShardUtils.cacheRegion(shard), shard.name(), normalizedQuery);
        return summoners;
    }

    public static List<Choice> autocomplete(String query, LeagueShard shard) {
        if (query == null || query.isBlank()) return new ArrayList<>();

        String normalizedQuery = normalizeSearch(query);
        if (normalizedQuery.isEmpty()) return new ArrayList<>();

        String key = RedisKey.SUMMONER_AUTOCOMPLETE.of(LeagueShardUtils.cacheRegion(shard), shard.name(), normalizedQuery);
        List<SummonerAutocompleteChoice> cached = RedisClient.get(key, SUMMONER_AUTOCOMPLETE_TYPE);
        if (cached != null) return toChoices(cached);

        List<SummonerAutocompleteChoice> choices = new ArrayList<>();
        for (MongoDB.SummonerSearchResult row : querySearch(normalizedQuery, shard)) {
            choices.add(new SummonerAutocompleteChoice(row.summoner().riotId(), row.summoner().puuid()));
        }
        RedisClient.set(RedisKey.SUMMONER_AUTOCOMPLETE, choices, LeagueShardUtils.cacheRegion(shard), shard.name(), normalizedQuery);
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

        RedisClient.delete(RedisKey.R4J_SUMMONER.of(shard.name(), puuid));
        RedisClient.delete(RedisKey.R4J_ACCOUNT.of(shard.name(), puuid));
        RedisClient.delete(RedisKey.R4J_USER_ID_BY_PUUID.of(shard.name(), puuid));
        RedisClient.delete(RedisKey.R4J_LEAGUE_ENTRIES.of(shard.name(), puuid));
        RedisClient.delete(RedisKey.R4J_CHAMPION_MASTERIES.of(shard.name(), puuid));
        RedisClient.delete(RedisKey.SUMMONER_RANK.of(LeagueShardUtils.cacheRegion(shard), shard.name(), puuid));
        RedisClient.delete(RedisKey.SUMMONER_RANKS.of(LeagueShardUtils.cacheRegion(shard), shard.name(), puuid));
        RedisClient.delete(RedisKey.SUMMONER_MASTERIES.of(LeagueShardUtils.cacheRegion(shard), shard.name(), puuid));
        RedisClient.delete(RedisKey.R4J_SPECTATOR_CURRENT.of(shard.name(), puuid));
        RedisClient.delete(RedisKey.R4J_MATCH_LIST.of(shard.name(), puuid, "null", 0));
        RedisClient.delete(RedisKey.SUMMONER.of(LeagueShardUtils.cacheRegion(shard), shard.name(), puuid));
        ProfileService.invalidate(puuid, shard);
    }

    // ============================================================================

    private static CompletableFuture<RiotAccount> refreshRiotAccountAsync(String puuid, LeagueShard shard) {
        return QueueHandler.immediate(RiotScheduler.class, shard, shard.name() + ":account-refresh:" + puuid,
            "account refresh puuid=" + puuid, ignored -> {
            RiotAccount account = RIOT_API.getAccountAPI().getAccountByPUUID(
                com.safjnest.lol.utils.LeagueShardUtils.getAccountRegion(shard), puuid);
            if (account != null) RedisClient.set(RedisKey.R4J_ACCOUNT, account, shard.name(), puuid);
            return account;
        });
    }

    private static CompletableFuture<no.stelar7.api.r4j.pojo.lol.summoner.Summoner> refreshRiotSummonerAsync(
        String puuid,
        LeagueShard shard
    ) {
        return QueueHandler.immediate(RiotScheduler.class, shard, shard.name() + ":summoner-refresh:" + puuid,
            "summoner refresh puuid=" + puuid, ignored -> {
            no.stelar7.api.r4j.pojo.lol.summoner.Summoner summoner =
                RIOT_API.getLoLAPI().getSummonerAPI().getSummonerByPUUID(shard, puuid);
            if (summoner != null) RedisClient.set(RedisKey.R4J_SUMMONER, summoner, shard.name(), puuid);
            return summoner;
        });
    }

    private static CompletableFuture<RefreshResult> refreshProfile(
        no.stelar7.api.r4j.pojo.lol.summoner.Summoner source,
        RiotAccount account
    ) {
        if (source == null || source.getPUUID() == null || source.getPlatform() == null) {
            return CompletableFuture.completedFuture(RefreshResult.ignored());
        }

        String riotId = account == null ? null : account.getName() + "#" + account.getTag();
        Summoner profile = store(persist(source, riotId, null), source.getPlatform());
        if (profile == null) return CompletableFuture.completedFuture(RefreshResult.ignored());

        LeagueShard sourceShard = source.getPlatform();
        return RankService.refreshAsync(source.getPUUID(), sourceShard)
            .thenCompose(ignored -> MasteryService.refreshAsync(source.getPUUID(), sourceShard))
            .thenApply(ignored -> {
                ProfileService.refreshProfile(profile, sourceShard);
                return new RefreshResult(source, true);
            });
    }

    private static void invalidateRefreshSourceCaches(String puuid, LeagueShard shard) {
        RedisClient.delete(List.of(
            RedisKey.R4J_SUMMONER.of(shard.name(), puuid),
            RedisKey.R4J_ACCOUNT.of(shard.name(), puuid),
            RedisKey.R4J_LEAGUE_ENTRIES.of(shard.name(), puuid),
            RedisKey.R4J_CHAMPION_MASTERIES.of(shard.name(), puuid),
            RedisKey.SUMMONER.of(LeagueShardUtils.cacheRegion(shard), shard.name(), puuid),
            RedisKey.SUMMONER_RANK.of(LeagueShardUtils.cacheRegion(shard), shard.name(), puuid),
            RedisKey.SUMMONER_RANKS.of(LeagueShardUtils.cacheRegion(shard), shard.name(), puuid),
            RedisKey.SUMMONER_MASTERIES.of(LeagueShardUtils.cacheRegion(shard), shard.name(), puuid),
            RedisKey.R4J_SPECTATOR_CURRENT.of(shard.name(), puuid)
        ));
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

            Map<GameQueueType, com.safjnest.lol.model.summoner.Rank> ranks = RankService.find(puuid, shard);
            List<Mastery> masteries = MasteryService.find(puuid, shard);
            Map<Integer, com.safjnest.lol.model.statistics.shared.ProfileLeafStats> championStats = championStats(
                profileService.getStatistics(summoner, shard, Filter.canonical()), entry.getValue());
            result.put(puuid, new LiveGame.ProfileOverview(summoner, ranks, masteries, championStats));
        }
        return result;
    }

    private static Map<Integer, com.safjnest.lol.model.statistics.shared.ProfileLeafStats> championStats(ProfileStatistics statistics, Integer championId) {
        if (statistics == null) return Map.of();
        Map<Integer, com.safjnest.lol.model.statistics.shared.ProfileLeafStats> available = statistics.championStats();
        List<Integer> ids = new ArrayList<>(available.keySet());
        ids.sort(Comparator.comparingLong((Integer id) -> available.get(id).games).reversed().thenComparingInt(Integer::intValue));
        Map<Integer, com.safjnest.lol.model.statistics.shared.ProfileLeafStats> result = new LinkedHashMap<>();
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

    private static Summoner cache(String puuid, LeagueShard shard) {
        return RedisClient.get(RedisKey.SUMMONER.of(LeagueShardUtils.cacheRegion(shard), shard.name(), puuid), Summoner.class);
    }

    private static Summoner query(String puuid, LeagueShard shard) {
        return MongoDB.findSummoner(puuid, shard);
    }

    private static no.stelar7.api.r4j.pojo.lol.summoner.Summoner cacheRiotSummoner(String puuid, LeagueShard shard) {
        return RedisClient.get(RedisKey.R4J_SUMMONER.of(shard.name(), puuid), no.stelar7.api.r4j.pojo.lol.summoner.Summoner.class);
    }

    private static CompletableFuture<Summoner> saveAsync(no.stelar7.api.r4j.pojo.lol.summoner.Summoner source) {
        String riotId = MongoDB.findSummonerName(source.getPUUID(), source.getPlatform());
        if (riotId != null && !riotId.isBlank()) return CompletableFuture.completedFuture(store(
            persist(source, riotId, null), source.getPlatform()));

        return getRiotAccountAsync(source.getPUUID(), source.getPlatform()).thenApply(account -> store(
            persist(source, account == null ? null : account.getName() + "#" + account.getTag(), null),
            source.getPlatform()
        ));
    }

    private static Summoner persist(
            no.stelar7.api.r4j.pojo.lol.summoner.Summoner source,
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
        if (summoner != null) RedisClient.set(RedisKey.SUMMONER, summoner, LeagueShardUtils.cacheRegion(shard), shard.name(), summoner.puuid());
        return summoner;
    }

    private static String queryPuuid(String name, String tag, LeagueShard shard) {
        if (!valid(name, tag, shard)) return null;
        return MongoDB.findPuuid(name + "#" + tag, shard);
    }

    private static List<MongoDB.SummonerSearchResult> querySearch(String normalizedQuery, LeagueShard shard) {
        return MongoDB.findSummonerSearch(normalizedQuery, shard, 25);
    }

    private static List<Choice> toChoices(List<SummonerAutocompleteChoice> autocompleteChoices) {
        List<Choice> choices = new ArrayList<>();
        for (SummonerAutocompleteChoice choice : autocompleteChoices) choices.add(new Choice(choice.riotId(), choice.puuid()));
        return choices;
    }

}
