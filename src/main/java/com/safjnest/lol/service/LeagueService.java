package com.safjnest.lol.service;

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
import com.safjnest.lol.model.match.Participant;
import com.safjnest.lol.model.summoner.Mastery;
import com.safjnest.lol.model.summoner.Rank;
import com.safjnest.lol.model.summoner.Summoner;
import com.safjnest.lol.model.summoner.SummonerView;
import com.safjnest.lol.model.statistics.ProfileStatistics;
import com.safjnest.lol.utils.GameQueueTypeUtils;
import com.safjnest.lol.utils.LeagueShardUtils;
import com.safjnest.lol.tracker.Tracker;
import com.safjnest.redis.RedisClient;
import com.safjnest.redis.RedisKey;
import com.safjnest.sql.QueryRecord;
import com.safjnest.sql.QueryResult;
import com.safjnest.sql.database.LeagueDB;

import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import org.json.JSONArray;
import org.json.JSONObject;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.api.regions.RegionShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierDivisionType;
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

        id = LeagueDB.getSummonerIdByPuuid(puuid, shard);
        if (id != 0) RedisClient.set(key, id, TTL_SUMMONER);
        return id;
    }

    public static String getUserIdByLOLAccountId(String puuid, LeagueShard shard) {
        String key = RedisKey.USER_ID_BY_PUUID.of(shard.name(), puuid);
        String userId = RedisClient.get(key, String.class);
        if (userId != null) return userId;

        userId = LeagueDB.getUserIdByLOLAccountId(puuid, shard);
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

        QueryResult rows = LeagueDB.searchSummoners(normalizedQuery, shard);
        List<Integer> summonerIds = new ArrayList<>();
        for (QueryRecord row : rows) summonerIds.add(row.getAsInt("summoner_id"));
        Map<Integer, Rank> ranks = getProfileRanks(summonerIds);

        List<SummonerView> summoners = new ArrayList<>();
        for (QueryRecord row : rows) {
            Summoner summoner = toSummoner(row);
            Rank rank = ranks.getOrDefault(summoner.summonerId(), Rank.unranked());
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
        QueryRecord row = LeagueDB.getProfileBase(puuid, shard);
        Summoner profile = !row.isEmpty() ? toSummoner(row) : null;
        if (profile != null) RedisClient.set(key, profile, TTL_PROFILE_BASE);
        return profile;
    }

    public static Rank getProfileRank(int summonerId) {
        String key = RedisKey.PROFILE_RANK.of(summonerId);
        Rank cached = RedisClient.get(key, Rank.class);
        if (cached != null) return cached;

        QueryRecord row = LeagueDB.getProfileRank(summonerId);
        Rank rank = !row.isEmpty() ? toRank(row) : Rank.unranked();
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

        Map<Integer, QueryRecord> rows = LeagueDB.getProfileRanks(missing);
        for (int summonerId : missing) {
            QueryRecord row = rows.get(summonerId);
            Rank rank = row != null ? toRank(row) : Rank.unranked();
            result.put(summonerId, rank);
            RedisClient.set(RedisKey.PROFILE_RANK.of(summonerId), rank, TTL_PROFILE_RANK);
        }
        return result;
    }

    public static List<Rank> getProfileRanks(int summonerId) {
        String key = RedisKey.PROFILE_RANKS.of(summonerId);
        List<Rank> cached = RedisClient.get(key, PROFILE_RANKS_TYPE);
        if (cached != null) return cached;

        QueryResult rows = LeagueDB.getProfileRanks(summonerId);
        List<Rank> ranks = new ArrayList<>();
        for (QueryRecord row : rows) ranks.add(toRank(row));
        if (rows.isSuccess()) RedisClient.set(key, ranks, TTL_PROFILE_RANK);
        return ranks;
    }

    public static List<Rank> getProfileRanksFromDatabase(int summonerId) {
        QueryResult rows = LeagueDB.getProfileRanks(summonerId);
        List<Rank> ranks = new ArrayList<>();
        for (QueryRecord row : rows) ranks.add(toRank(row));
        if (rows.isSuccess()) RedisClient.set(RedisKey.PROFILE_RANKS.of(summonerId), ranks, TTL_PROFILE_RANK);
        return ranks;
    }

    public static List<Rank> getProfileRanks(String puuid, LeagueShard shard) {
        List<Rank> ranks = new ArrayList<>();
        for (LeagueEntry entry : getLeagueEntries(puuid, shard)) {
            ranks.add(new Rank(entry.getQueueType(), entry.getTierDivisionType(), entry.getLeaguePoints(), entry.getWins(), entry.getLosses()));
        }
        return ranks;
    }

    public static List<Mastery> getProfileMasteries(int summonerId) {
        String key = RedisKey.PROFILE_MASTERIES.of(summonerId);
        List<Mastery> cached = RedisClient.get(key, PROFILE_MASTERIES_TYPE);
        if (cached != null) return cached;

        QueryResult rows = LeagueDB.getProfileMasteries(summonerId);
        List<Mastery> masteries = new ArrayList<>();
        for (QueryRecord row : rows) {
            masteries.add(new Mastery(
                row.getAsInt("champion_id"), row.getAsInt("champion_level"), row.getAsInt("champion_points")
            ));
        }
        if (rows.isSuccess()) RedisClient.set(key, masteries, TTL_PROFILE_MASTERIES);
        return masteries;
    }

    public static List<String> getProfileSeasonPuuids(LeagueShard shard, long seasonStart, long seasonEnd) {
        List<String> puuids = new ArrayList<>();
        for (QueryRecord row : LeagueDB.getProfileSeasonSummoners(shard, seasonStart, seasonEnd)) {
            String puuid = row.get("puuid");
            if (puuid != null && !puuid.isBlank()) puuids.add(puuid);
        }
        return puuids;
    }

    public static List<MatchResult> getProfileMatchesAfter(int summonerId, long afterTimeEnd, long untilTimeEnd) {
        Map<String, MatchResult> matches = new LinkedHashMap<>();
        Map<String, Integer> teamKills = new HashMap<>();
        Map<String, List<Participant>> participants = new HashMap<>();
        for (QueryRecord row : LeagueDB.getProfileMatchesAfter(summonerId, afterTimeEnd, untilTimeEnd)) {
            String gameId = row.get("game_id");
            MatchResult match = matches.computeIfAbsent(gameId, ignored -> toMatchResult(row));
            boolean ally = isAlly(match.queue(), row);

            if (ally) teamKills.merge(gameId, kills(row.get("participant_kda")), Integer::sum);
            participants.computeIfAbsent(gameId, ignored -> new ArrayList<>()).add(Participant.forMatchResult(
                row.getAsInt("participant_champion"), row.get("participant_puuid"), row.get("participant_team")
            ));
        }

        List<MatchResult> result = new ArrayList<>();
        for (MatchResult match : matches.values()) {
            result.add(new MatchResult(
                match.gameId(), match.queue(), match.timeStart(), match.timeEnd(), match.win(), match.kda(), match.championId(),
                match.lane(), match.damage(), match.cs(), match.gold(), match.vision(), teamKills.getOrDefault(match.gameId(), 0),
                match.items(), match.summonerSpells(), participants.getOrDefault(match.gameId(), List.of())
            ));
        }
        return result;
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
        QueryResult result = LeagueDB.getAdvancedLOLData(summonerId, time_start, time_end, queue);
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

        Match match = LeagueDB.getMatch(shard, databaseGameId);
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
      QueryResult result = LeagueDB.getSummonerData(puuid, shard);
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
        QueryResult summoners = LeagueDB.getFocusedSummoners(normalizedQuery, shard);

        for (QueryRecord summoner : summoners) {
            autocompleteChoices.add(new SummonerAutocompleteChoice(
                summoner.get("riot_id"),
                summoner.get("puuid")
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

    private static Summoner toSummoner(QueryRecord row) {
        return new Summoner(
            row.getAsInt("summoner_id"),
            row.get("puuid"),
            row.get("riot_id"),
            row.get("region"),
            row.getAsInt("level"),
            row.getAsInt("icon")
        );
    }

    private static Rank toRank(QueryRecord row) {
        return new Rank(
            queue(row.getOrDefault("queue", "RANKED_SOLO_5X5")),
            tierDivision(row.getOrDefault("rank", "UNRANKED")),
            row.getAsInt("lp"),
            row.getAsInt("wins"),
            row.getAsInt("losses")
        );
    }

    private static TierDivisionType tierDivision(String value) {
        try { return TierDivisionType.valueOf(value); }
        catch (Exception ignored) { return TierDivisionType.UNRANKED; }
    }

    private static GameQueueType queue(String value) {
        try { return GameQueueType.valueOf(value); }
        catch (Exception ignored) { return GameQueueType.TEAM_BUILDER_RANKED_SOLO; }
    }

    private static MatchResult toMatchResult(QueryRecord row) {
        return MatchResult.of(
            row.get("game_id"),
            queue(row.get("queue")),
            timeMs(row.get("time_start")),
            timeMs(row.get("time_end")),
            row.getAsBoolean("win"),
            row.get("kda"),
            row.getAsInt("champion"),
            lane(row.get("lane")),
            row.getAsInt("damage"),
            row.getAsInt("cs"),
            row.getAsInt("gold_earned"),
            row.getAsInt("vision_score"),
            row.getAsInt("team_kills"),
            items(row),
            summonerSpells(row),
            List.of()
        );
    }

    private static boolean isAlly(GameQueueType queue, QueryRecord row) {
        if (GameQueueTypeUtils.isCherry(queue)) {
            return row.getAsInt("player_subteam") != 0 &&
                row.getAsInt("player_subteam") == row.getAsInt("participant_subteam");
        }

        String team = row.get("player_team");
        return team != null && team.equals(row.get("participant_team"));
    }

    private static List<Integer> items(QueryRecord row) {
        JSONObject items = build(row).optJSONObject("items");
        if (items == null) return new ArrayList<>();

        return new ArrayList<>(List.of(
            items.optInt("0", 0),
            items.optInt("1", 0),
            items.optInt("2", 0),
            items.optInt("3", 0),
            items.optInt("4", 0),
            items.optInt("5", 0),
            items.optInt("6", 0)
        ));
    }

    private static int kills(String kda) {
        if (kda == null) return 0;
        try { return Integer.parseInt(kda.split("/", 2)[0]); }
        catch (Exception ignored) { return 0; }
    }

    private static LaneType lane(String value) {
        try { return LaneType.valueOf(value); }
        catch (Exception ignored) { return null; }
    }

    private static List<Integer> summonerSpells(QueryRecord row) {
        JSONArray spells = build(row).optJSONArray("summoner_spells");
        if (spells == null) return new ArrayList<>();

        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < spells.length(); i++) {
            result.add(spells.optInt(i, 0));
        }
        return result;
    }

    private static JSONObject build(QueryRecord row) {
        try {
            String build = row.get("build");
            return build != null && !build.isBlank() ? new JSONObject(build) : new JSONObject();
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private static long timeMs(String value) {
        try {
            return java.sql.Timestamp.valueOf(value).getTime();
        } catch (Exception e) {
            return 0;
        }
    }

}
