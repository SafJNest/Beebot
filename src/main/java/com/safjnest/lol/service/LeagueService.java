package com.safjnest.lol.service;

import com.safjnest.mongo.MongoDB;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
import com.safjnest.mongo.MongoRecord;
import com.safjnest.lol.utils.LeagueShardUtils;
import com.safjnest.lol.tracker.Tracker;
import com.safjnest.redis.RedisClient;
import com.safjnest.redis.RedisKey;
import com.safjnest.sql.QueryRecord;
import com.safjnest.sql.QueryResult;

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

    static {
      riotApi = LeagueHandler.getRiotApi();
    }

    private static final int TTL_SUMMONER = 0;
    private static final int TTL_ACCOUNT = 0;
    private static final int TTL_LEAGUE_ENTRIES = 60 * 60 * 24; // 24 hours
    private static final int TTL_CHAMPION_MASTERIES = 60 * 60 * 24; // 24 hours
    private static final int TTL_SPECTATOR = 600;
    private static final int TTL_ADVANCED_LOL_DATA = 60 * 60 * 24; // 24 hours
    private static final int TTL_MATCH_LIST = 60 * 60 * 4; // 12 hours
    private static final int TTL_MATCH = 0; // never expire
    private static final int TTL_MATCH_DETAIL = 0;
    private static final int TTL_SUMMONER_AUTOCOMPLETE = 60 * 60 * 24; // 24 hours
    private static final int TTL_SUMMONER_SEARCH = 60 * 15;
    private static final int TTL_PROFILE_BASE = 60 * 60;
    private static final int TTL_PROFILE_RANK = 60 * 15;
    private static final int TTL_PROFILE_MASTERIES = 60 * 60;

    private static final TypeReference<List<LeagueEntry>> LEAGUE_ENTRIES_TYPE =
        new TypeReference<List<LeagueEntry>>() {};
    private static final TypeReference<List<ChampionMastery>> CHAMPION_MASTERIES_TYPE =
        new TypeReference<List<ChampionMastery>>() {};
    private static final TypeReference<List<SummonerView>> SUMMONER_SEARCH_TYPE =
        new TypeReference<List<SummonerView>>() {};
    private static final TypeReference<List<Rank>> PROFILE_RANKS_TYPE =
        new TypeReference<List<Rank>>() {};
    private static final TypeReference<List<Mastery>> PROFILE_MASTERIES_TYPE =
        new TypeReference<List<Mastery>>() {};

    private static final TypeReference<List<SummonerAutocompleteChoice>> SUMMONER_AUTOCOMPLETE_TYPE = new TypeReference<>() {};

    private static R4J riotApi;
    public static no.stelar7.api.r4j.pojo.lol.summoner.Summoner getSummonerByPuuid(String puuid, LeagueShard shard) {
        String key = RedisKey.SUMMONER.of(shard.name(), puuid);
        no.stelar7.api.r4j.pojo.lol.summoner.Summoner summoner = RedisClient.get(
            key, no.stelar7.api.r4j.pojo.lol.summoner.Summoner.class
        );
        if (summoner != null) return summoner;

        try { summoner = riotApi.getLoLAPI().getSummonerAPI().getSummonerByPUUID(shard, puuid); } 
        catch (Exception e) { return null; }
        if (summoner != null) RedisClient.set(key, summoner, TTL_SUMMONER);
        return summoner;
    }

    public static int getSummonerIdByPuuid(String puuid, LeagueShard shard) {
        String key = RedisKey.SUMMONER_ID.of(shard.name(), puuid);
        Integer id = RedisClient.get(key, Integer.class);
        if (id != null) return id;

        Summoner profile = MongoDB.findSummoner(puuid, shard);
        id = profile == null ? 0 : profile.summonerId();
        if (id != 0) RedisClient.set(key, id, TTL_SUMMONER);
        return id;
    }

    public static String getUserIdByLOLAccountId(String puuid, LeagueShard shard) {
        String key = RedisKey.USER_ID_BY_PUUID.of(shard.name(), puuid);
        String userId = RedisClient.get(key, String.class);
        if (userId != null) return userId;

        userId = MongoDB.findUserIdByPuuid(puuid, shard);
        if (userId != null) RedisClient.set(key, userId, TTL_SUMMONER);
        return userId;
    }

    public static RiotAccount getRiotAccountByPuuid(String puuid, LeagueShard shard) {
        String key = RedisKey.ACCOUNT.of(shard.name(), puuid);
        RiotAccount account = RedisClient.get(key, RiotAccount.class);
        if (account != null) return account;

        try { account = riotApi.getAccountAPI().getAccountByPUUID(LeagueShardUtils.getAccountRegion(shard), puuid); } 
        catch (Exception e) { return null; }

        if (account != null) RedisClient.set(key, account, TTL_ACCOUNT);
        return account;
    }

    public static RiotAccount getRiotAccountByName(String name, String tag, LeagueShard shard) {
        String key = RedisKey.ACCOUNT_BY_NAME.of(shard.name(), name, tag);
        RiotAccount account = RedisClient.get(key, RiotAccount.class);
        if (account != null) return account;

        try { account = riotApi.getAccountAPI().getAccountByTag(LeagueShardUtils.getAccountRegion(shard), name, tag); } 
        catch (Exception e) { return null; }

        if (account != null) RedisClient.set(key, account, TTL_ACCOUNT);
        return account;
    }

    public static String getPuuidByRiotId(String name, String tag, LeagueShard shard) {
        RiotAccount account = getRiotAccountByName(name, tag, shard);
        return account != null ? account.getPUUID() : null;
    }

    public static RiotAccount getRiotAccountFromSummoner(no.stelar7.api.r4j.pojo.lol.summoner.Summoner s) {
        return getRiotAccountByPuuid(s.getPUUID(), s.getPlatform());
    }

    public static no.stelar7.api.r4j.pojo.lol.summoner.Summoner getSummonerByName(String name, String tag, LeagueShard shard) {
        RiotAccount account = getRiotAccountByName(name, tag, shard);
        return account != null 
            ? getSummonerByPuuid(account.getPUUID(), shard) 
            : null;
    }

    public static void invalidateSummoner(String puuid, LeagueShard shard) {
        RedisClient.delete(RedisKey.SUMMONER.of(shard.name(), puuid));
        RedisClient.delete(RedisKey.ACCOUNT.of(shard.name(), puuid));
        RedisClient.delete(RedisKey.SUMMONER_ID.of(shard.name(), puuid));
        RedisClient.delete(RedisKey.LEAGUE_ENTRIES.of(shard.name(), puuid));
        RedisClient.delete(RedisKey.CHAMPION_MASTERIES.of(shard.name(), puuid));
        RedisClient.delete(RedisKey.SPECTATOR_CURRENT.of(shard.name(), puuid));
        RedisClient.delete(RedisKey.MATCH_LIST.of(shard.name(), puuid, "null", 0));
        RedisClient.delete(RedisKey.PROFILE_BASE.of(shard.name(), puuid));
        invalidateProfilePage(puuid, shard);

        int summonerId = getSummonerIdByPuuid(puuid, shard);
        if (summonerId != 0) {
            RedisClient.delete(RedisKey.PROFILE_RANK.of(summonerId));
            RedisClient.delete(RedisKey.PROFILE_RANKS.of(summonerId));
            RedisClient.delete(RedisKey.PROFILE_MASTERIES.of(summonerId));
        }
    }

    public static void invalidateProfilePage(String puuid, LeagueShard shard) {
        if (puuid == null || puuid.isBlank() || shard == null) return;
        RedisClient.delete(RedisKey.PROFILE_PAGE.of(shard.name(), puuid));
    }

    public static List<SummonerView> searchSummoners(String query, LeagueShard shard) {
        String normalizedQuery = normalizeSearch(query);
        String key = RedisKey.SUMMONER_SEARCH.of(shard.name(), normalizedQuery);
        List<SummonerView> cached = RedisClient.get(key, SUMMONER_SEARCH_TYPE);
        if (cached != null) return cached;

        List<Summoner> rows = MongoDB.findSummonersByRiotId(normalizedQuery, shard, 25);
        List<String> puuids = new ArrayList<>();
        for (Summoner summoner : rows) puuids.add(summoner.puuid());
        Map<String, Rank> ranks = MongoDB.findSoloRanksByPuuid(puuids, shard);

        List<SummonerView> summoners = new ArrayList<>();
        for (Summoner summoner : rows) {
            Rank rank = ranks.getOrDefault(summoner.puuid(), Rank.unranked());
            summoners.add(SummonerView.from(summoner, List.of(rank), new ProfileStatistics(), List.of()));
        }
        RedisClient.set(key, summoners, TTL_SUMMONER_SEARCH);
        return summoners;
    }

    public static Summoner getProfileBase(String puuid, LeagueShard shard) {
        String key = RedisKey.PROFILE_BASE.of(shard.name(), puuid);
        Summoner cached = RedisClient.get(key, Summoner.class);
        if (cached != null) return cached;

        return getProfileBaseFromDatabase(puuid, shard);
    }

    public static Summoner getProfileBaseFromDatabase(String puuid, LeagueShard shard) {
        String key = RedisKey.PROFILE_BASE.of(shard.name(), puuid);
        Summoner profile = MongoDB.findSummoner(puuid, shard);
        if (profile != null) RedisClient.set(key, profile, TTL_PROFILE_BASE);
        return profile;
    }

    public static Rank getProfileRank(int summonerId) {
        String key = RedisKey.PROFILE_RANK.of(summonerId);
        Rank cached = RedisClient.get(key, Rank.class);
        if (cached != null) return cached;

        Summoner profile = MongoDB.findSummonerByLegacyId(summonerId);
        Rank rank = profile == null ? Rank.unranked() : MongoDB.findRank(
                profile.puuid(), LeagueShard.valueOf(profile.region()), GameQueueType.RANKED_SOLO_5X5);
        if (rank == null) rank = Rank.unranked();
        RedisClient.set(key, rank, TTL_PROFILE_RANK);
        return rank;
    }

    public static Rank getProfileRank(String puuid, LeagueShard shard) {
        String key = RedisKey.PROFILE_RANK.of(shard.name() + ":" + puuid);
        Rank cached = RedisClient.get(key, Rank.class);
        if (cached != null) return cached;

        LeagueEntry entry = getLeagueEntry(puuid, shard, "5v5 Ranked Solo");
        Rank rank = entry != null
            ? new Rank(entry.getQueueType(), entry.getTierDivisionType(), entry.getLeaguePoints(), entry.getWins(), entry.getLosses())
            : Rank.unranked();
        RedisClient.set(key, rank, TTL_PROFILE_RANK);
        return rank;
    }

    public static Map<Integer, Rank> getProfileRanks(List<Integer> summonerIds) {
        Map<Integer, Rank> result = new HashMap<>();
        if (summonerIds == null || summonerIds.isEmpty()) return result;

        Map<String, Integer> idsByKey = new LinkedHashMap<>();
        for (int summonerId : summonerIds) {
            if (summonerId != 0) idsByKey.put(RedisKey.PROFILE_RANK.of(summonerId), summonerId);
        }
        if (idsByKey.isEmpty()) return result;

        Map<String, Rank> cached = RedisClient.get(new ArrayList<>(idsByKey.keySet()), Rank.class);
        for (Map.Entry<String, Rank> entry : cached.entrySet()) {
            Integer summonerId = idsByKey.get(entry.getKey());
            if (summonerId != null) result.put(summonerId, entry.getValue());
        }

        List<Integer> missing = new ArrayList<>();
        for (int summonerId : idsByKey.values()) {
            if (!result.containsKey(summonerId)) missing.add(summonerId);
        }
        if (missing.isEmpty()) return result;

        for (int summonerId : missing) {
            Summoner profile = MongoDB.findSummonerByLegacyId(summonerId);
            Rank rank = profile == null ? Rank.unranked() : MongoDB.findRank(
                    profile.puuid(), LeagueShard.valueOf(profile.region()), GameQueueType.RANKED_SOLO_5X5);
            if (rank == null) rank = Rank.unranked();
            result.put(summonerId, rank);
            RedisClient.set(RedisKey.PROFILE_RANK.of(summonerId), rank, TTL_PROFILE_RANK);
        }
        return result;
    }

    public static List<Rank> getProfileRanks(int summonerId) {
        String key = RedisKey.PROFILE_RANKS.of(summonerId);
        List<Rank> cached = RedisClient.get(key, PROFILE_RANKS_TYPE);
        if (cached != null) return cached;

        Summoner profile = MongoDB.findSummonerByLegacyId(summonerId);
        List<Rank> ranks = profile == null ? List.of() : MongoDB.findRanks(
                profile.puuid(), LeagueShard.valueOf(profile.region()));
        RedisClient.set(key, ranks, TTL_PROFILE_RANK);
        return ranks;
    }

    public static List<Rank> getProfileRanksFromDatabase(int summonerId) {
        Summoner profile = MongoDB.findSummonerByLegacyId(summonerId);
        List<Rank> ranks = profile == null ? List.of() : MongoDB.findRanks(
                profile.puuid(), LeagueShard.valueOf(profile.region()));
        RedisClient.set(RedisKey.PROFILE_RANKS.of(summonerId), ranks, TTL_PROFILE_RANK);
        return ranks;
    }

    public static List<Rank> getProfileRanks(String puuid, LeagueShard shard) {
        return MongoDB.findRanks(puuid, shard);
    }

    public static List<Mastery> getProfileMasteries(String puuid, LeagueShard shard) {
        return MongoDB.findMasteries(puuid, shard);
    }

    public static List<Mastery> getProfileMasteries(int summonerId) {
        String key = RedisKey.PROFILE_MASTERIES.of(summonerId);
        List<Mastery> cached = RedisClient.get(key, PROFILE_MASTERIES_TYPE);
        if (cached != null) return cached;

        Summoner profile = MongoDB.findSummonerByLegacyId(summonerId);
        List<Mastery> masteries = profile == null ? List.of() : MongoDB.findMasteries(
                profile.puuid(), LeagueShard.valueOf(profile.region()));
        RedisClient.set(key, masteries, TTL_PROFILE_MASTERIES);
        return masteries;
    }

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

    public static List<MatchResult> getProfileMatchesAfter(int summonerId, long afterTimeEnd, long untilTimeEnd) {
        Summoner profile = MongoDB.findSummonerByLegacyId(summonerId);
        return profile == null ? List.of() : getProfileMatchesAfter(profile.puuid(), LeagueShard.valueOf(profile.region()),
                afterTimeEnd, untilTimeEnd, null);
    }

    public static List<LeagueEntry> getLeagueEntries(String puuid, LeagueShard shard) {
        String key = RedisKey.LEAGUE_ENTRIES.of(shard.name(), puuid);
        List<LeagueEntry> cached = RedisClient.get(key, LEAGUE_ENTRIES_TYPE);
        if (cached != null) {
            return cached;
        }
        try {
            List<LeagueEntry> entries = riotApi.getLoLAPI().getLeagueAPI().getLeagueEntriesByPUUID(shard, puuid);
            if (entries == null) {
                entries = new ArrayList<>();
            }
            RedisClient.set(key, entries, TTL_LEAGUE_ENTRIES);
            return entries;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public static LeagueEntry getLeagueEntry(String puuid, LeagueShard shard, String queueCommonName) {
        for (LeagueEntry entry : getLeagueEntries(puuid, shard)) {
            if (entry.getQueueType().commonName().equals(queueCommonName)) {
                return entry;
            }
        }
        return null;
    }

    public static List<ChampionMastery> getChampionMasteries(String puuid, LeagueShard shard) {
        String key = RedisKey.CHAMPION_MASTERIES.of(shard.name(), puuid);
        List<ChampionMastery> cached = RedisClient.get(key, CHAMPION_MASTERIES_TYPE);
        if (cached != null) {
            return cached;
        }
        try {
            List<ChampionMastery> list = riotApi.getLoLAPI().getMasteryAPI().getChampionMasteries(shard, puuid);
            if (list == null) {
                list = new ArrayList<>();
            }
            RedisClient.set(key, list, TTL_CHAMPION_MASTERIES);
            return list;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public static SpectatorGameInfo getSpectatorGame(String puuid, LeagueShard shard) {
        String key = RedisKey.SPECTATOR_CURRENT.of(shard.name(), puuid);
        SpectatorGameInfo cached = RedisClient.get(key, SpectatorGameInfo.class);
        if (cached != null) {
            return cached;
        }
        try {
            SpectatorGameInfo game = riotApi.getLoLAPI().getSpectatorAPI().getCurrentGame(shard, puuid);
            if (game != null) {
                RedisClient.set(key, game, TTL_SPECTATOR);
            }
            return game;
        } catch (Exception e) {
            return null;
        }
    }

    public static QueryResult getAdvancedLOLData(int summonerId, long time_start, long time_end, GameQueueType queue) {
        String key = RedisKey.ADVANCED_LOL_DATA.of(summonerId, time_start, time_end, queue != null ? queue.name() : "null");
        QueryResult cached = RedisClient.get(key, QueryResult.class);
        if (cached != null) {
            return cached;
        }
        Summoner profile = MongoDB.findSummonerByLegacyId(summonerId);
        QueryResult result = profile == null ? toQueryResult(List.of()) : toQueryResult(MongoDB.findAdvancedProfileProjections(
                profile.puuid(), LeagueShard.valueOf(profile.region()), time_start, time_end, queue));
        if (result != null) {
            RedisClient.set(key, result, TTL_ADVANCED_LOL_DATA);
        }
        return result;
    }

    public static List<String> getMatchList(no.stelar7.api.r4j.pojo.lol.summoner.Summoner summoner, GameQueueType queue, int index) {
      String queueKey = queue != null ? queue.name() : "null";
      String key = RedisKey.MATCH_LIST.of(summoner.getPlatform().name(), summoner.getPUUID(), queueKey, index);
      List<String> cached = RedisClient.get(key, new TypeReference<List<String>>() {});
      if (cached != null) {
        return cached;
      }
      List<String> matchList = summoner.getLeagueGames().withQueue(queue).withBeginIndex(index).get();
      if (matchList != null) 
        RedisClient.set(key, matchList, TTL_MATCH_LIST);

      return matchList != null ? matchList : new ArrayList<>();
    }

    public static LOLMatch getMatch(String gameId, LeagueShard shard) {
      RegionShard region = shard.toRegionShard();
      String key = RedisKey.MATCH.of(region.name(), gameId);
      LOLMatch cached = RedisClient.get(key, LOLMatch.class);
      if (cached != null) {
        return cached;
      }
      LOLMatch match = riotApi.getLoLAPI().getMatchAPI().getMatch(region, gameId);
      if (match != null) {
        RedisClient.set(key, match, TTL_MATCH);
      }
      return match;
    }

    public static ApiResult<Match> getMatchDetail(String gameId, LeagueShard shard) {
        String databaseGameId = databaseGameId(gameId);
        String key = RedisKey.MATCH_DETAIL.of(shard.name(), databaseGameId);
        Match cached = RedisClient.get(key, Match.class);
        if (cached != null) {
            cached.restoreEvents();
            return ApiResult.ready(cached);
        }

        Match match = MongoDB.findMatch(shard.name() + "_" + databaseGameId);
        if (match != null) {
            RedisClient.delete(RedisKey.MATCH_NOT_FOUND.of(shard.name(), databaseGameId));
            RedisClient.set(key, match, TTL_MATCH_DETAIL);
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

    public static String putMatch(LOLMatch match) {
      String gameId = match.getPlatform().name() + "_" + match.getGameId();
      RegionShard region = match.getPlatform().toRegionShard();
      String key = RedisKey.MATCH.of(region.name(), gameId);
      RedisClient.set(key, match, TTL_MATCH);
      RedisClient.delete(RedisKey.MATCH_NOT_FOUND.of(match.getPlatform().name(), String.valueOf(match.getGameId())));
      return gameId;
    }

    public static QueryResult getSummonerData(String puuid, LeagueShard shard) {
      String key = RedisKey.SUMMONER_DATA.of(puuid, shard.name());
      QueryResult cached = RedisClient.get(key, QueryResult.class);
      if (cached != null) {
        return cached;
      }
      QueryResult result = toQueryResult(MongoDB.findSummonerData(
          puuid, shard, 0, Long.MAX_VALUE, GameQueueType.TEAM_BUILDER_RANKED_SOLO));
      if (result != null) {
        RedisClient.set(key, result, TTL_MATCH);
      }
      return result;
    }

    public static void putLeagueEntry(LeagueShard shard, LeagueEntry entry) {
        String key = RedisKey.LEAGUE_ENTRIES.of(shard.name(), entry.getPuuid());

        List<LeagueEntry> entries = RedisClient.get(key, LEAGUE_ENTRIES_TYPE);
        if (entries == null) {
            entries = new ArrayList<>();
        }
        boolean updated = false;
        for (int i = 0; i < entries.size(); i++) {
            LeagueEntry current = entries.get(i);

            if (current.getQueueType() == entry.getQueueType()) {
                entries.set(i, entry);
                updated = true;
                break;
            }
        }
        if (!updated) {
            entries.add(entry);
        }
        RedisClient.set(key, entries, TTL_LEAGUE_ENTRIES);
    }

    public static void puWeaktLeagueEntry(LeagueShard shard, LeagueEntry entry) {
        //TODO: implement weak key
        String key = RedisKey.LEAGUE_ENTRIES.of(shard, entry.getPuuid());
        RedisClient.set(key, List.of(entry), TTL_LEAGUE_ENTRIES);
    }

    public static List<Choice> getSummonerAutocomplete(String query, LeagueShard shard) {
        if (query == null || query.isBlank()) {
            return new ArrayList<>();
        }

        String normalizedQuery = normalizeSearch(query);
        if (normalizedQuery.isEmpty()) {
            return new ArrayList<>();
        }
        String key = RedisKey.SUMMONER_AUTOCOMPLETE.of(shard.name(), normalizedQuery);

        List<SummonerAutocompleteChoice> cached = RedisClient.get(key, SUMMONER_AUTOCOMPLETE_TYPE);
        if (cached != null) {
            return toChoices(cached);
        }

        List<SummonerAutocompleteChoice> autocompleteChoices = new ArrayList<>();
        for (MongoRecord summoner : MongoDB.findFocusedSummoners(normalizedQuery, shard, 25)) {
            autocompleteChoices.add(new SummonerAutocompleteChoice(
                summoner.getAsString("riotId") != null ? summoner.getAsString("riotId") : summoner.getAsString("riot_id"),
                summoner.getAsString("puuid")
            ));
        }

        RedisClient.set(key, autocompleteChoices, TTL_SUMMONER_AUTOCOMPLETE);

        return toChoices(autocompleteChoices);
    }

    public static String normalizeSearch(String query) {
        if (query == null) return "";

        String lowerCaseQuery = query.toLowerCase(Locale.ROOT);
        StringBuilder normalizedQuery = new StringBuilder();
        for (int i = 0; i < lowerCaseQuery.length(); i++) {
            char character = lowerCaseQuery.charAt(i);
            if (!Character.isWhitespace(character) && character != '-' && character != '#') {
                normalizedQuery.append(character);
            }
        }
        return normalizedQuery.toString();
    }

    // ============================================================================

    private static String databaseGameId(String gameId) {
        int separator = gameId.indexOf('_');
        return separator >= 0 ? gameId.substring(separator + 1) : gameId;
    }

    private static List<Choice> toChoices(List<SummonerAutocompleteChoice> autocompleteChoices) {
        List<Choice> choices = new ArrayList<>();
    
        for (SummonerAutocompleteChoice choice : autocompleteChoices) {
            choices.add(new Choice(choice.riotId(), choice.puuid()));
        }
    
        return choices;
    }

    private static QueryResult toQueryResult(List<MongoRecord> records) {
        QueryResult result = new QueryResult();
        if (records == null) {
            result.setSuccess(true);
            return result;
        }
        for (MongoRecord record : records) {
            QueryRecord row = new QueryRecord();
            for (Map.Entry<String, Object> entry : record.toDocument().entrySet()) {
                if (entry.getValue() != null) row.put(entry.getKey(), String.valueOf(entry.getValue()));
            }
            result.add(row);
        }
        result.setSuccess(true);
        return result;
    }

}
