package com.safjnest.lol.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import com.fasterxml.jackson.core.type.TypeReference;
import com.safjnest.lol.LeagueHandler;
import com.safjnest.lol.model.ApiResult;
import com.safjnest.lol.model.match.Match;
import com.safjnest.lol.model.match.MatchResult;
import com.safjnest.lol.model.summoner.Mastery;
import com.safjnest.lol.model.summoner.Rank;
import com.safjnest.lol.model.summoner.Summoner;
import com.safjnest.lol.model.summoner.SummonerView;
import com.safjnest.lol.model.statistics.ProfileStatistics;
import com.safjnest.lol.tracker.Tracker;
import com.safjnest.lol.utils.GameQueueTypeUtils;
import com.safjnest.lol.utils.LeagueShardUtils;
import com.safjnest.lol.utils.TierDivisionUtils;
import com.safjnest.mongo.MongoDB;
import com.safjnest.redis.RedisClient;
import com.safjnest.redis.RedisKey;
import com.safjnest.sql.QueryRecord;

import net.dv8tion.jda.api.interactions.commands.Command.Choice;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.api.regions.RegionShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.impl.R4J;
import no.stelar7.api.r4j.pojo.lol.championmastery.ChampionMastery;
import no.stelar7.api.r4j.pojo.lol.league.LeagueEntry;
import no.stelar7.api.r4j.pojo.lol.match.v5.LOLMatch;
import no.stelar7.api.r4j.pojo.lol.spectator.SpectatorGameInfo;
import no.stelar7.api.r4j.pojo.shared.RiotAccount;

public class LeagueService {

    private record SummonerAutocompleteChoice(String riotId, String puuid) {}

    private static final int TTL_SUMMONER = 0;
    private static final int TTL_ACCOUNT = 0;
    private static final int TTL_LEAGUE_ENTRIES = 60 * 60 * 24;
    private static final int TTL_CHAMPION_MASTERIES = 60 * 60 * 24;
    private static final int TTL_PROFILE_COMPONENT = 60 * 15;
    private static final int TTL_SPECTATOR = 600;
    private static final int TTL_ADVANCED_LOL_DATA = 60 * 60 * 24;
    private static final int TTL_MATCH_LIST = 60 * 60 * 4;
    private static final int TTL_MATCH = 0;
    private static final int TTL_MATCH_DETAIL = 0;
    private static final int TTL_SUMMONER_AUTOCOMPLETE = 60 * 60 * 24;
    private static final int TTL_SUMMONER_SEARCH = 60 * 15;
    private static final int TTL_PROFILE_BASE = 60 * 60;

    private static final TypeReference<List<LeagueEntry>> LEAGUE_ENTRIES_TYPE =
        new TypeReference<List<LeagueEntry>>() {};
    private static final TypeReference<List<ChampionMastery>> CHAMPION_MASTERIES_TYPE =
        new TypeReference<List<ChampionMastery>>() {};
    private static final TypeReference<List<SummonerView>> SUMMONER_SEARCH_TYPE =
        new TypeReference<List<SummonerView>>() {};
    private static final TypeReference<List<SummonerAutocompleteChoice>> SUMMONER_AUTOCOMPLETE_TYPE =
        new TypeReference<>() {};

    private static final ExecutorService FETCH_EXECUTOR = Executors.newThreadPerTaskExecutor(
        Thread.ofVirtual().name("league-fetch-", 0).factory()
    );
    private static final Map<String, CompletableFuture<no.stelar7.api.r4j.pojo.lol.summoner.Summoner>> RIOT_SUMMONERS =
        new ConcurrentHashMap<>();
    private static final Map<String, CompletableFuture<RiotAccount>> ACCOUNTS = new ConcurrentHashMap<>();
    private static final Map<String, CompletableFuture<List<LeagueEntry>>> LEAGUE_ENTRIES = new ConcurrentHashMap<>();
    private static final Map<String, CompletableFuture<List<Rank>>> RANKS = new ConcurrentHashMap<>();
    private static final Map<String, CompletableFuture<List<Mastery>>> MASTERIES = new ConcurrentHashMap<>();
    private static final Map<String, CompletableFuture<LOLMatch>> R4J_MATCHES = new ConcurrentHashMap<>();
    private static final Map<String, CompletableFuture<Match>> MATCHES = new ConcurrentHashMap<>();

    private static R4J riotApi;

    static {
        riotApi = LeagueHandler.getRiotApi();
    }

    // SUMMONER

    public static Summoner getSavedSummoner(String puuid, LeagueShard shard) {
        if (!valid(puuid, shard)) return null;

        String key = RedisKey.PROFILE_BASE.of(shard.name(), puuid);
        Summoner cached = RedisClient.get(key, Summoner.class);
        if (cached != null) return cached;

        Summoner stored = MongoDB.findSummoner(puuid, shard);
        if (stored != null) RedisClient.set(key, stored, TTL_PROFILE_BASE);
        return stored;
    }

    public static CompletableFuture<Summoner> fetchSummoner(String puuid, LeagueShard shard) {
        if (!valid(puuid, shard)) return CompletableFuture.completedFuture(null);

        String key = resourceKey(shard, puuid);
        return shared(RIOT_SUMMONERS, key, () -> {
            no.stelar7.api.r4j.pojo.lol.summoner.Summoner summoner =
                riotApi.getLoLAPI().getSummonerAPI().getSummonerByPUUID(shard, puuid);
            if (summoner != null) saveSummoner(summoner);
            return summoner;
        }).thenApply(ignored -> getSavedSummoner(puuid, shard));
    }

    public static CompletableFuture<Summoner> getAsyncSummoner(String puuid, LeagueShard shard) {
        Summoner saved = getSavedSummoner(puuid, shard);
        return saved != null ? CompletableFuture.completedFuture(saved) : fetchSummoner(puuid, shard);
    }

    public static Summoner getSummoner(String puuid, LeagueShard shard) {
        try {
            return getAsyncSummoner(puuid, shard).join();
        } catch (CompletionException exception) {
            return null;
        }
    }

    public static no.stelar7.api.r4j.pojo.lol.summoner.Summoner getRiotSummoner(String puuid, LeagueShard shard) {
        if (!valid(puuid, shard)) return null;

        no.stelar7.api.r4j.pojo.lol.summoner.Summoner cached = getSavedRiotSummoner(puuid, shard);
        if (cached != null) return cached;

        try {
            return getAsyncRiotSummoner(puuid, shard).join();
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

    public static boolean upsertSummoner(
            no.stelar7.api.r4j.pojo.lol.summoner.Summoner summoner,
            String userId) {
        return upsertSummoner(summoner, userId, null);
    }

    public static String getUserIdByLOLAccountId(String puuid, LeagueShard shard) {
        if (!valid(puuid, shard)) return null;

        String key = RedisKey.USER_ID_BY_PUUID.of(shard.name(), puuid);
        String userId = RedisClient.get(key, String.class);
        if (userId != null) return userId;

        userId = MongoDB.findUserIdByPuuid(puuid, shard);
        if (userId != null) RedisClient.set(key, userId, TTL_SUMMONER);
        return userId;
    }

    // ACCOUNT

    public static RiotAccount getSavedAccount(String puuid, LeagueShard shard) {
        if (!valid(puuid, shard)) return null;
        return RedisClient.get(RedisKey.ACCOUNT.of(shard.name(), puuid), RiotAccount.class);
    }

    public static RiotAccount getSavedAccount(String name, String tag, LeagueShard shard) {
        if (!valid(name, tag, shard)) return null;
        return RedisClient.get(RedisKey.ACCOUNT_BY_NAME.of(shard.name(), name, tag), RiotAccount.class);
    }

    public static CompletableFuture<RiotAccount> fetchAccount(String puuid, LeagueShard shard) {
        if (!valid(puuid, shard)) return CompletableFuture.completedFuture(null);

        String key = resourceKey(shard, puuid);
        return shared(ACCOUNTS, key, () -> {
            RiotAccount account = riotApi.getAccountAPI().getAccountByPUUID(
                LeagueShardUtils.getAccountRegion(shard), puuid);
            if (account != null) RedisClient.set(RedisKey.ACCOUNT.of(shard.name(), puuid), account, TTL_ACCOUNT);
            return account;
        });
    }

    public static CompletableFuture<RiotAccount> fetchAccount(String name, String tag, LeagueShard shard) {
        if (!valid(name, tag, shard)) return CompletableFuture.completedFuture(null);

        String key = resourceKey(shard, name + "#" + tag);
        return shared(ACCOUNTS, key, () -> {
            RiotAccount account = riotApi.getAccountAPI().getAccountByTag(
                LeagueShardUtils.getAccountRegion(shard), name, tag);
            if (account != null) {
                RedisClient.set(RedisKey.ACCOUNT_BY_NAME.of(shard.name(), name, tag), account, TTL_ACCOUNT);
                RedisClient.set(RedisKey.ACCOUNT.of(shard.name(), account.getPUUID()), account, TTL_ACCOUNT);
            }
            return account;
        });
    }

    public static CompletableFuture<RiotAccount> getAsyncAccount(String puuid, LeagueShard shard) {
        RiotAccount saved = getSavedAccount(puuid, shard);
        return saved != null ? CompletableFuture.completedFuture(saved) : fetchAccount(puuid, shard);
    }

    public static CompletableFuture<RiotAccount> getAsyncAccount(String name, String tag, LeagueShard shard) {
        RiotAccount saved = getSavedAccount(name, tag, shard);
        return saved != null ? CompletableFuture.completedFuture(saved) : fetchAccount(name, tag, shard);
    }

    public static RiotAccount getAccount(String puuid, LeagueShard shard) {
        try {
            return getAsyncAccount(puuid, shard).join();
        } catch (CompletionException exception) {
            return null;
        }
    }

    public static RiotAccount getAccount(String name, String tag, LeagueShard shard) {
        try {
            return getAsyncAccount(name, tag, shard).join();
        } catch (CompletionException exception) {
            return null;
        }
    }

    public static String getPuuidByRiotId(String name, String tag, LeagueShard shard) {
        String databasePuuid = MongoDB.findPuuid(name + "#" + tag, shard);
        if (databasePuuid != null) return databasePuuid;

        RiotAccount account = getAccount(name, tag, shard);
        return account == null ? null : account.getPUUID();
    }

    public static CompletableFuture<String> getAsyncPuuidByRiotId(String name, String tag, LeagueShard shard) {
        String databasePuuid = MongoDB.findPuuid(name + "#" + tag, shard);
        if (databasePuuid != null) return CompletableFuture.completedFuture(databasePuuid);

        return getAsyncAccount(name, tag, shard).thenApply(account -> account == null ? null : account.getPUUID());
    }

    public static RiotAccount getAccountFromSummoner(
            no.stelar7.api.r4j.pojo.lol.summoner.Summoner summoner) {
        return summoner == null ? null : getAccount(summoner.getPUUID(), summoner.getPlatform());
    }

    // RANK

    public static List<Rank> getSavedRanks(String puuid, LeagueShard shard) {
        if (!valid(puuid, shard)) return null;

        String key = RedisKey.PROFILE_RANKS.of(shard.name(), puuid);
        List<Rank> cached = RedisClient.get(key, new TypeReference<List<Rank>>() {});
        if (cached != null) return cached;

        List<Rank> stored = MongoDB.findRanks(puuid, shard);
        if (stored != null) RedisClient.set(key, stored, TTL_PROFILE_COMPONENT);
        return stored;
    }

    public static CompletableFuture<List<Rank>> fetchRanks(String puuid, LeagueShard shard) {
        if (!valid(puuid, shard)) return CompletableFuture.completedFuture(List.of());

        String key = resourceKey(shard, puuid);
        return shared(RANKS, key, () -> {
            if (getAsyncSummoner(puuid, shard).join() == null) {
                throw new IllegalStateException("Summoner is not available for rank persistence");
            }
            List<LeagueEntry> entries = getAsyncLeagueEntries(puuid, shard).join();
            List<Rank> ranks = toRanks(entries);
            saveRankData(puuid, shard, ranks);
            return ranks;
        });
    }

    public static CompletableFuture<List<Rank>> getAsyncRanks(String puuid, LeagueShard shard) {
        List<Rank> saved = getSavedRanks(puuid, shard);
        return saved != null ? CompletableFuture.completedFuture(saved) : fetchRanks(puuid, shard);
    }

    public static List<Rank> getRanks(String puuid, LeagueShard shard) {
        try {
            return getAsyncRanks(puuid, shard).join();
        } catch (CompletionException exception) {
            return List.of();
        }
    }

    public static Rank getRank(String puuid, LeagueShard shard, GameQueueType queue) {
        GameQueueType selectedQueue = GameQueueTypeUtils.canonicalQueue(
            queue == null ? GameQueueType.RANKED_SOLO_5X5 : queue);
        for (Rank rank : getRanks(puuid, shard)) {
            if (rank.queue() == selectedQueue) return rank;
        }
        return unranked(selectedQueue);
    }

    public static List<LeagueEntry> getLeagueEntries(String puuid, LeagueShard shard) {
        if (!valid(puuid, shard)) return new ArrayList<>();

        try {
            return getAsyncLeagueEntries(puuid, shard).join();
        } catch (CompletionException exception) {
            return new ArrayList<>();
        }
    }

    public static LeagueEntry getLeagueEntry(String puuid, LeagueShard shard, String queueCommonName) {
        for (LeagueEntry entry : getLeagueEntries(puuid, shard)) {
            if (entry.getQueueType().commonName().equals(queueCommonName)) return entry;
        }
        return null;
    }

    public static void putLeagueEntry(LeagueShard shard, LeagueEntry entry) {
        if (entry == null || shard == null || entry.getPuuid() == null) return;

        String key = RedisKey.LEAGUE_ENTRIES.of(shard.name(), entry.getPuuid());
        List<LeagueEntry> entries = getSavedLeagueEntries(entry.getPuuid(), shard);
        if (entries == null) entries = new ArrayList<>();

        boolean updated = false;
        for (int index = 0; index < entries.size(); index++) {
            if (entries.get(index).getQueueType() == entry.getQueueType()) {
                entries.set(index, entry);
                updated = true;
                break;
            }
        }
        if (!updated) entries.add(entry);

        RedisClient.set(key, entries, TTL_LEAGUE_ENTRIES);
        if (getSummoner(entry.getPuuid(), shard) == null) return;
        saveRankData(entry.getPuuid(), shard, toRanks(entries));
    }

    public static void putWeakLeagueEntry(LeagueShard shard, LeagueEntry entry) {
        putLeagueEntry(shard, entry);
    }

    public static void saveRanks(String puuid, LeagueShard shard, List<LeagueEntry> entries) {
        if (!valid(puuid, shard) || entries == null) return;

        List<Rank> ranks = toRanks(entries);
        Map<GameQueueType, Long> mmr = new ConcurrentHashMap<>();
        for (Rank rank : ranks) {
            long rankMmr = TierDivisionUtils.getMmr(rank.tier(), rank.lp());
            mmr.put(rank.queue(), rankMmr);
            MongoDB.upsertLeaderboardEntry(puuid, shard, rank, rankMmr);
        }
        MongoDB.upsertRanks(puuid, shard, ranks, mmr);
        RedisClient.set(RedisKey.PROFILE_RANKS.of(shard.name(), puuid), ranks, TTL_PROFILE_COMPONENT);
        invalidateProfilePage(puuid, shard);
    }

    // MASTERIES

    public static List<Mastery> getSavedMasteries(String puuid, LeagueShard shard) {
        if (!valid(puuid, shard)) return null;

        String key = RedisKey.PROFILE_MASTERIES.of(shard.name(), puuid);
        List<Mastery> cached = RedisClient.get(key, new TypeReference<List<Mastery>>() {});
        if (cached != null) return cached;

        List<Mastery> stored = MongoDB.findMasteries(puuid, shard);
        if (stored != null) RedisClient.set(key, stored, TTL_PROFILE_COMPONENT);
        return stored;
    }

    public static CompletableFuture<List<Mastery>> fetchMasteries(String puuid, LeagueShard shard) {
        if (!valid(puuid, shard)) return CompletableFuture.completedFuture(List.of());

        String key = resourceKey(shard, puuid);
        return shared(MASTERIES, key, () -> {
            if (getAsyncSummoner(puuid, shard).join() == null) {
                throw new IllegalStateException("Summoner is not available for mastery persistence");
            }
            List<ChampionMastery> riotMasteries = getSavedRiotMasteries(puuid, shard);
            if (riotMasteries == null) {
                riotMasteries = riotApi.getLoLAPI().getMasteryAPI().getChampionMasteries(shard, puuid);
                if (riotMasteries == null) throw new IllegalStateException("Riot returned no mastery result");
                RedisClient.set(
                    RedisKey.CHAMPION_MASTERIES.of(shard.name(), puuid),
                    riotMasteries,
                    TTL_CHAMPION_MASTERIES
                );
            }

            List<Mastery> masteries = toMasteries(riotMasteries);
            saveMasteries(puuid, shard, masteries);
            return masteries;
        });
    }

    public static CompletableFuture<List<Mastery>> getAsyncMasteries(String puuid, LeagueShard shard) {
        List<Mastery> saved = getSavedMasteries(puuid, shard);
        return saved != null ? CompletableFuture.completedFuture(saved) : fetchMasteries(puuid, shard);
    }

    public static List<Mastery> getMasteries(String puuid, LeagueShard shard) {
        try {
            return getAsyncMasteries(puuid, shard).join();
        } catch (CompletionException exception) {
            return List.of();
        }
    }

    public static Mastery getMastery(String puuid, LeagueShard shard, int championId) {
        for (Mastery mastery : getMasteries(puuid, shard)) {
            if (mastery.championId() == championId) return mastery;
        }
        return null;
    }

    // SPECTATOR

    public static SpectatorGameInfo getSpectatorGame(String puuid, LeagueShard shard) {
        if (!valid(puuid, shard)) return null;

        String key = RedisKey.SPECTATOR_CURRENT.of(shard.name(), puuid);
        SpectatorGameInfo cached = RedisClient.get(key, SpectatorGameInfo.class);
        if (cached != null) return cached;

        try {
            SpectatorGameInfo game = riotApi.getLoLAPI().getSpectatorAPI().getCurrentGame(shard, puuid);
            if (game != null) RedisClient.set(key, game, TTL_SPECTATOR);
            return game;
        } catch (Exception exception) {
            return null;
        }
    }

    // MATCH

    public static Match getSavedMatch(String gameId, LeagueShard shard) {
        if (!valid(gameId, shard)) return null;

        String databaseGameId = databaseGameId(gameId);
        String key = RedisKey.MATCH_DETAIL.of(shard.name(), databaseGameId);
        Match cached = RedisClient.get(key, Match.class);
        if (cached != null) {
            cached.restoreEvents();
            return cached;
        }

        Match stored = MongoDB.findMatch(fullGameId(gameId, shard));
        if (stored != null) {
            stored.restoreEvents();
            RedisClient.set(key, stored, TTL_MATCH_DETAIL);
        }
        return stored;
    }

    public static CompletableFuture<Match> fetchMatch(String gameId, LeagueShard shard) {
        if (!valid(gameId, shard)) return CompletableFuture.completedFuture(null);

        String key = resourceKey(shard, fullGameId(gameId, shard));
        return shared(MATCHES, key, () -> {
            LOLMatch source = getAsyncR4JMatch(gameId, shard).join();
            if (source == null) return null;

            Match match = Match.fromR4J(source);
            if (match != null) saveMatch(match);
            return match;
        });
    }

    public static CompletableFuture<Match> getAsyncMatch(String gameId, LeagueShard shard) {
        Match saved = getSavedMatch(gameId, shard);
        return saved != null ? CompletableFuture.completedFuture(saved) : fetchMatch(gameId, shard);
    }

    public static Match getMatch(String gameId, LeagueShard shard) {
        try {
            return getAsyncMatch(gameId, shard).join();
        } catch (CompletionException exception) {
            return null;
        }
    }

    public static LOLMatch getSavedR4JMatch(String gameId, LeagueShard shard) {
        if (!valid(gameId, shard)) return null;

        RegionShard region = shard.toRegionShard();
        return RedisClient.get(
            RedisKey.MATCH.of(region.name(), fullGameId(gameId, shard)),
            LOLMatch.class
        );
    }

    public static CompletableFuture<LOLMatch> fetchR4JMatch(String gameId, LeagueShard shard) {
        if (!valid(gameId, shard)) return CompletableFuture.completedFuture(null);

        RegionShard region = shard.toRegionShard();
        String fullGameId = fullGameId(gameId, shard);
        String key = resourceKey(region.name(), fullGameId);
        return shared(R4J_MATCHES, key, () -> {
            LOLMatch match = riotApi.getLoLAPI().getMatchAPI().getMatch(region, fullGameId);
            if (match != null) {
                RedisClient.set(RedisKey.MATCH.of(region.name(), fullGameId), match, TTL_MATCH);
            }
            return match;
        });
    }

    public static CompletableFuture<LOLMatch> getAsyncR4JMatch(String gameId, LeagueShard shard) {
        LOLMatch saved = getSavedR4JMatch(gameId, shard);
        return saved != null ? CompletableFuture.completedFuture(saved) : fetchR4JMatch(gameId, shard);
    }

    public static LOLMatch getR4JMatch(String gameId, LeagueShard shard) {
        try {
            return getAsyncR4JMatch(gameId, shard).join();
        } catch (CompletionException exception) {
            return null;
        }
    }

    public static ApiResult<Match> getMatchDetail(String gameId, LeagueShard shard) {
        String databaseGameId = databaseGameId(gameId);
        Match match = getSavedMatch(databaseGameId, shard);
        if (match != null) {
            RedisClient.delete(RedisKey.MATCH_NOT_FOUND.of(shard.name(), databaseGameId));
            return ApiResult.ready(match);
        }

        String notFound = RedisClient.get(RedisKey.MATCH_NOT_FOUND.of(shard.name(), databaseGameId));
        if ("1".equals(notFound)) return ApiResult.notFound();

        Tracker.enqueueMatchLookup(shard, databaseGameId);
        return ApiResult.pending();
    }

    public static void invalidateMatchDetail(LeagueShard shard, String gameId) {
        String databaseGameId = databaseGameId(gameId);
        RedisClient.delete(RedisKey.MATCH_DETAIL.of(shard.name(), databaseGameId));
        RedisClient.delete(RedisKey.MATCH_NOT_FOUND.of(shard.name(), databaseGameId));
    }

    public static String putR4JMatch(LOLMatch match) {
        String gameId = fullGameId(String.valueOf(match.getGameId()), match.getPlatform());
        RegionShard region = match.getPlatform().toRegionShard();
        RedisClient.set(RedisKey.MATCH.of(region.name(), gameId), match, TTL_MATCH);
        RedisClient.delete(RedisKey.MATCH_NOT_FOUND.of(match.getPlatform().name(), String.valueOf(match.getGameId())));
        return gameId;
    }

    public static List<String> getMatches(
            no.stelar7.api.r4j.pojo.lol.summoner.Summoner summoner,
            GameQueueType queue,
            int index) {
        if (summoner == null) return new ArrayList<>();

        String queueKey = queue != null ? queue.name() : "null";
        String key = RedisKey.MATCH_LIST.of(summoner.getPlatform().name(), summoner.getPUUID(), queueKey, index);
        List<String> cached = RedisClient.get(key, new TypeReference<List<String>>() {});
        if (cached != null) return cached;

        List<String> matchList = summoner.getLeagueGames().withQueue(queue).withBeginIndex(index).get();
        if (matchList != null) RedisClient.set(key, matchList, TTL_MATCH_LIST);
        return matchList != null ? matchList : new ArrayList<>();
    }

    // SEARCH

    public static List<SummonerView> searchSummoners(String query, LeagueShard shard) {
        String normalizedQuery = normalizeSearch(query);
        String key = RedisKey.SUMMONER_SEARCH.of(shard.name(), normalizedQuery);
        List<SummonerView> cached = RedisClient.get(key, SUMMONER_SEARCH_TYPE);
        if (cached != null) return cached;

        List<SummonerView> summoners = new ArrayList<>();
        for (MongoDB.SummonerSearchResult row : MongoDB.findSummonerSearch(normalizedQuery, shard, 25)) {
            Rank rank = row.soloRank() != null ? row.soloRank() : Rank.unranked();
            summoners.add(SummonerView.from(row.summoner(), List.of(rank), new ProfileStatistics(), List.of()));
        }
        RedisClient.set(key, summoners, TTL_SUMMONER_SEARCH);
        return summoners;
    }

    public static List<Choice> getSummonerAutocomplete(String query, LeagueShard shard) {
        if (query == null || query.isBlank()) return new ArrayList<>();

        String normalizedQuery = normalizeSearch(query);
        if (normalizedQuery.isEmpty()) return new ArrayList<>();

        String key = RedisKey.SUMMONER_AUTOCOMPLETE.of(shard.name(), normalizedQuery);
        List<SummonerAutocompleteChoice> cached = RedisClient.get(key, SUMMONER_AUTOCOMPLETE_TYPE);
        if (cached != null) return toChoices(cached);

        List<SummonerAutocompleteChoice> choices = new ArrayList<>();
        for (MongoDB.SummonerSearchResult row : MongoDB.findSummonerSearch(normalizedQuery, shard, 25)) {
            choices.add(new SummonerAutocompleteChoice(row.summoner().riotId(), row.summoner().puuid()));
        }
        RedisClient.set(key, choices, TTL_SUMMONER_AUTOCOMPLETE);
        return toChoices(choices);
    }

    public static String normalizeSearch(String query) {
        if (query == null) return "";

        String lowerCaseQuery = query.toLowerCase(Locale.ROOT);
        StringBuilder normalizedQuery = new StringBuilder();
        for (int index = 0; index < lowerCaseQuery.length(); index++) {
            char character = lowerCaseQuery.charAt(index);
            if (!Character.isWhitespace(character) && character != '-' && character != '#') {
                normalizedQuery.append(character);
            }
        }
        return normalizedQuery.toString();
    }

    // INVALIDATION

    public static void invalidateSummoner(String puuid, LeagueShard shard) {
        if (!valid(puuid, shard)) return;

        RedisClient.delete(RedisKey.SUMMONER.of(shard.name(), puuid));
        RedisClient.delete(RedisKey.ACCOUNT.of(shard.name(), puuid));
        RedisClient.delete(RedisKey.USER_ID_BY_PUUID.of(shard.name(), puuid));
        RedisClient.delete(RedisKey.LEAGUE_ENTRIES.of(shard.name(), puuid));
        RedisClient.delete(RedisKey.CHAMPION_MASTERIES.of(shard.name(), puuid));
        RedisClient.delete(RedisKey.PROFILE_RANK.of(shard.name() + ":" + puuid));
        RedisClient.delete(RedisKey.PROFILE_RANKS.of(shard.name(), puuid));
        RedisClient.delete(RedisKey.PROFILE_MASTERIES.of(shard.name(), puuid));
        RedisClient.delete(RedisKey.SPECTATOR_CURRENT.of(shard.name(), puuid));
        RedisClient.delete(RedisKey.MATCH_LIST.of(shard.name(), puuid, "null", 0));
        RedisClient.delete(RedisKey.PROFILE_BASE.of(shard.name(), puuid));
        invalidateProfilePage(puuid, shard);
    }

    public static void invalidateProfilePage(String puuid, LeagueShard shard) {
        if (!valid(puuid, shard)) return;
        RedisClient.delete(RedisKey.PROFILE_PAGE.of(shard.name(), puuid));
    }

    // DATA

    public static List<String> getProfileSeasonPuuids(LeagueShard shard, long seasonStart, long seasonEnd) {
        return MongoDB.findSeasonSummonerPuuids(shard, seasonStart, seasonEnd);
    }

    public static List<MatchResult> getProfileMatchesAfter(
            String puuid,
            LeagueShard shard,
            long afterTimeEnd,
            long untilTimeEnd,
            GameQueueType queue) {
        return MongoDB.findMatchResults(puuid, shard, afterTimeEnd, untilTimeEnd, queue, 0, 100);
    }

    public static List<QueryRecord> getAdvancedLOLData(
            String puuid,
            LeagueShard shard,
            long timeStart,
            long timeEnd,
            GameQueueType queue) {
        String key = RedisKey.ADVANCED_LOL_DATA.of(
            puuid,
            timeStart,
            timeEnd,
            queue != null ? queue.name() : "null"
        );
        List<QueryRecord> cached = RedisClient.get(key, new TypeReference<List<QueryRecord>>() {});
        if (cached != null) return cached;

        List<QueryRecord> result = puuid == null || shard == null
            ? new ArrayList<>()
            : MongoDB.findAdvancedProfileProjections(puuid, shard, timeStart, timeEnd, queue);
        RedisClient.set(key, result, TTL_ADVANCED_LOL_DATA);
        return result;
    }

    public static List<QueryRecord> getSummonerData(String puuid, LeagueShard shard) {
        String key = RedisKey.SUMMONER_DATA.of(puuid, shard.name());
        List<QueryRecord> cached = RedisClient.get(key, new TypeReference<List<QueryRecord>>() {});
        if (cached != null) return cached;

        List<QueryRecord> result = MongoDB.findSummonerData(
            puuid,
            shard,
            0,
            Long.MAX_VALUE,
            GameQueueType.TEAM_BUILDER_RANKED_SOLO
        );
        RedisClient.set(key, result, TTL_MATCH);
        return result;
    }

    // PRIVATE FETCH, MAPPING AND SAVE

    private static CompletableFuture<no.stelar7.api.r4j.pojo.lol.summoner.Summoner> getAsyncRiotSummoner(
            String puuid,
            LeagueShard shard) {
        String key = resourceKey(shard, puuid);
        return shared(RIOT_SUMMONERS, key, () -> {
            no.stelar7.api.r4j.pojo.lol.summoner.Summoner summoner =
                riotApi.getLoLAPI().getSummonerAPI().getSummonerByPUUID(shard, puuid);
            if (summoner != null) saveSummoner(summoner);
            return summoner;
        });
    }

    private static no.stelar7.api.r4j.pojo.lol.summoner.Summoner getSavedRiotSummoner(
            String puuid,
            LeagueShard shard) {
        return RedisClient.get(
            RedisKey.SUMMONER.of(shard.name(), puuid),
            no.stelar7.api.r4j.pojo.lol.summoner.Summoner.class
        );
    }

    private static CompletableFuture<List<LeagueEntry>> getAsyncLeagueEntries(
            String puuid,
            LeagueShard shard) {
        List<LeagueEntry> cached = getSavedLeagueEntries(puuid, shard);
        if (cached != null) return CompletableFuture.completedFuture(cached);

        String key = resourceKey(shard, puuid);
        return shared(LEAGUE_ENTRIES, key, () -> {
            List<LeagueEntry> entries = riotApi.getLoLAPI().getLeagueAPI().getLeagueEntriesByPUUID(shard, puuid);
            if (entries == null) throw new IllegalStateException("Riot returned no rank result");

            RedisClient.set(RedisKey.LEAGUE_ENTRIES.of(shard.name(), puuid), entries, TTL_LEAGUE_ENTRIES);
            return entries;
        });
    }

    private static List<LeagueEntry> getSavedLeagueEntries(String puuid, LeagueShard shard) {
        if (!valid(puuid, shard)) return null;
        return RedisClient.get(RedisKey.LEAGUE_ENTRIES.of(shard.name(), puuid), LEAGUE_ENTRIES_TYPE);
    }

    private static List<Rank> toRanks(List<LeagueEntry> entries) {
        List<Rank> ranks = new ArrayList<>();
        if (entries == null) return ranks;

        for (LeagueEntry entry : entries) {
            if (entry == null || entry.getQueueType() == null) continue;
            GameQueueType queue = GameQueueTypeUtils.canonicalQueue(entry.getQueueType());
            ranks.add(new Rank(
                queue,
                entry.getTierDivisionType(),
                entry.getLeaguePoints(),
                entry.getWins(),
                entry.getLosses()
            ));
        }
        return ranks;
    }

    private static List<Mastery> toMasteries(List<ChampionMastery> entries) {
        List<Mastery> masteries = new ArrayList<>();
        if (entries == null) return masteries;

        for (ChampionMastery entry : entries) {
            if (entry == null) continue;
            masteries.add(new Mastery(
                entry.getChampionId(),
                entry.getChampionLevel(),
                entry.getChampionPoints()
            ));
        }
        return masteries;
    }

    private static Summoner saveSummoner(
            no.stelar7.api.r4j.pojo.lol.summoner.Summoner source) {
        String knownRiotId = MongoDB.findSummonerName(source.getPUUID(), source.getPlatform());
        String riotId = knownRiotId;
        if (riotId == null || riotId.isBlank()) {
            RiotAccount account = getAccount(source.getPUUID(), source.getPlatform());
            if (account != null) riotId = account.getName() + "#" + account.getTag();
        }

        Summoner result = new Summoner(
            0,
            source.getPUUID(),
            riotId,
            source.getPlatform().name(),
            source.getSummonerLevel(),
            source.getProfileIconId()
        );
        MongoDB.upsertSummoner(result, null);
        RedisClient.set(RedisKey.SUMMONER.of(source.getPlatform().name(), source.getPUUID()), source, TTL_SUMMONER);
        RedisClient.set(RedisKey.PROFILE_BASE.of(source.getPlatform().name(), source.getPUUID()), result, TTL_PROFILE_BASE);
        return result;
    }

    private static boolean upsertSummoner(
            no.stelar7.api.r4j.pojo.lol.summoner.Summoner summoner,
            String userId,
            String knownRiotId) {
        if (summoner == null || summoner.getPUUID() == null || summoner.getPlatform() == null) return false;

        String riotId = knownRiotId != null
            ? knownRiotId
            : MongoDB.findSummonerName(summoner.getPUUID(), summoner.getPlatform());
        if (riotId == null || riotId.isBlank()) {
            RiotAccount account = getAccount(summoner.getPUUID(), summoner.getPlatform());
            if (account != null) riotId = account.getName() + "#" + account.getTag();
        }

        return MongoDB.upsertSummoner(new Summoner(
            0,
            summoner.getPUUID(),
            riotId,
            summoner.getPlatform().name(),
            summoner.getSummonerLevel(),
            summoner.getProfileIconId()
        ), userId);
    }

    private static void saveRankData(String puuid, LeagueShard shard, List<Rank> ranks) {
        if (!valid(puuid, shard) || ranks == null) return;

        Map<GameQueueType, Long> mmr = new ConcurrentHashMap<>();
        for (Rank rank : ranks) {
            if (rank != null && rank.queue() != null) {
                mmr.put(rank.queue(), (long) TierDivisionUtils.getMmr(rank.tier(), rank.lp()));
            }
        }
        MongoDB.upsertRanks(puuid, shard, ranks, mmr);
        RedisClient.set(RedisKey.PROFILE_RANKS.of(shard.name(), puuid), ranks, TTL_PROFILE_COMPONENT);
        invalidateProfilePage(puuid, shard);
    }

    private static void saveMasteries(String puuid, LeagueShard shard, List<Mastery> masteries) {
        if (!valid(puuid, shard) || masteries == null) return;

        MongoDB.upsertMasteries(puuid, shard, masteries);
        RedisClient.set(RedisKey.PROFILE_MASTERIES.of(shard.name(), puuid), masteries, TTL_PROFILE_COMPONENT);
        invalidateProfilePage(puuid, shard);
    }

    private static List<ChampionMastery> getSavedRiotMasteries(String puuid, LeagueShard shard) {
        return RedisClient.get(
            RedisKey.CHAMPION_MASTERIES.of(shard.name(), puuid),
            CHAMPION_MASTERIES_TYPE
        );
    }

    private static Rank unranked(GameQueueType queue) {
        return new Rank(
            queue,
            no.stelar7.api.r4j.basic.constants.types.lol.TierDivisionType.UNRANKED,
            0,
            0,
            0
        );
    }

    private static String resourceKey(LeagueShard shard, String id) {
        return resourceKey(shard.name(), id);
    }

    private static String resourceKey(String scope, String id) {
        return scope + ":" + id;
    }

    private static boolean valid(String id, LeagueShard shard) {
        return shard != null && id != null && !id.isBlank();
    }

    private static boolean valid(String name, String tag, LeagueShard shard) {
        return shard != null && name != null && !name.isBlank() && tag != null && !tag.isBlank();
    }

    private static <T> CompletableFuture<T> shared(
            Map<String, CompletableFuture<T>> futures,
            String key,
            Supplier<T> supplier) {
        return futures.computeIfAbsent(key, ignored -> {
            CompletableFuture<T> future = CompletableFuture.supplyAsync(supplier, FETCH_EXECUTOR);
            future.whenComplete((value, error) -> futures.remove(key, future));
            return future;
        });
    }

    private static String databaseGameId(String gameId) {
        int separator = gameId.indexOf('_');
        return separator >= 0 ? gameId.substring(separator + 1) : gameId;
    }

    private static String fullGameId(String gameId, LeagueShard shard) {
        if (gameId == null || gameId.isBlank() || shard == null || gameId.indexOf('_') > 0) return gameId;
        return shard.name() + "_" + gameId;
    }

    private static void saveMatch(Match match) {
        if (match == null || match.gameId == null || match.gameId.isBlank() || match.leagueShard == null) return;

        String fullGameId = fullGameId(match.gameId, match.leagueShard);
        MongoDB.upsertMatch(fullGameId, match);
        RedisClient.set(
            RedisKey.MATCH_DETAIL.of(match.leagueShard.name(), databaseGameId(fullGameId)),
            match,
            TTL_MATCH_DETAIL
        );
    }

    private static List<Choice> toChoices(List<SummonerAutocompleteChoice> autocompleteChoices) {
        List<Choice> choices = new ArrayList<>();
        for (SummonerAutocompleteChoice choice : autocompleteChoices) {
            choices.add(new Choice(choice.riotId(), choice.puuid()));
        }
        return choices;
    }
}
