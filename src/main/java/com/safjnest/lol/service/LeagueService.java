package com.safjnest.lol.service;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.safjnest.lol.LeagueHandler;
import com.safjnest.lol.model.ProfileChampionStats;
import com.safjnest.lol.model.ProfileMatch;
import com.safjnest.lol.model.SummonerProfile;
import com.safjnest.lol.model.SummonerRank;
import com.safjnest.lol.model.SummonerSearchResult;
import com.safjnest.lol.utils.LeagueShardUtils;
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
import no.stelar7.api.r4j.impl.R4J;
import no.stelar7.api.r4j.pojo.lol.championmastery.ChampionMastery;
import no.stelar7.api.r4j.pojo.lol.league.LeagueEntry;
import no.stelar7.api.r4j.pojo.lol.match.v5.LOLMatch;
import no.stelar7.api.r4j.pojo.lol.spectator.SpectatorGameInfo;
import no.stelar7.api.r4j.pojo.lol.summoner.Summoner;
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
    private static final int TTL_SUMMONER_AUTOCOMPLETE = 60 * 60 * 24; // 24 hours
    private static final int TTL_SUMMONER_SEARCH = 60 * 15;
    private static final int TTL_PROFILE_BASE = 60 * 60;
    private static final int TTL_PROFILE_RANK = 60 * 15;
    private static final int TTL_PROFILE_RECENT_MATCHES = 60 * 5;
    private static final int TTL_PROFILE_TOP_CHAMPIONS = 60 * 15;
    private static final int PROFILE_DEFAULT_RECENT_LIMIT = 20;
    private static final int PROFILE_DEFAULT_TOP_CHAMPIONS_LIMIT = 6;

    private static final TypeReference<List<LeagueEntry>> LEAGUE_ENTRIES_TYPE =
        new TypeReference<List<LeagueEntry>>() {};
    private static final TypeReference<List<ChampionMastery>> CHAMPION_MASTERIES_TYPE =
        new TypeReference<List<ChampionMastery>>() {};
    private static final TypeReference<List<SummonerSearchResult>> SUMMONER_SEARCH_TYPE =
        new TypeReference<List<SummonerSearchResult>>() {};
    private static final TypeReference<List<ProfileMatch>> PROFILE_MATCHES_TYPE =
        new TypeReference<List<ProfileMatch>>() {};
    private static final TypeReference<List<ProfileChampionStats>> PROFILE_CHAMPION_STATS_TYPE =
        new TypeReference<List<ProfileChampionStats>>() {};

    private static final TypeReference<List<SummonerAutocompleteChoice>> SUMMONER_AUTOCOMPLETE_TYPE = new TypeReference<>() {};

    private static R4J riotApi;

    public static Summoner getSummonerByPuuid(String puuid, LeagueShard shard) {
        String key = RedisKey.SUMMONER.of(shard.name(), puuid);
        Summoner summoner = RedisClient.get(key, Summoner.class);
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

    public static RiotAccount getRiotAccountFromSummoner(Summoner s) {
        return getRiotAccountByPuuid(s.getPUUID(), s.getPlatform());
    }

    public static Summoner getSummonerByName(String name, String tag, LeagueShard shard) {
        RiotAccount account = getRiotAccountByName(name, tag, shard);
        return account != null 
            ? getSummonerByPuuid(account.getPUUID(), shard) 
            : null;
    }

    public static void invalidateSummoner(String puuid, LeagueShard shard) {
        RedisClient.delete(RedisKey.SUMMONER.of(shard.name(), puuid));
        RedisClient.delete(RedisKey.ACCOUNT.of(shard.name(), puuid));
        RedisClient.delete(RedisKey.LEAGUE_ENTRIES.of(shard.name(), puuid));
        RedisClient.delete(RedisKey.CHAMPION_MASTERIES.of(shard.name(), puuid));
        RedisClient.delete(RedisKey.SPECTATOR_CURRENT.of(shard.name(), puuid));
        RedisClient.delete(RedisKey.MATCH_LIST.of(shard.name(), puuid, "null", 0));
        RedisClient.delete(RedisKey.PROFILE_BASE.of(shard.name(), puuid));

        int summonerId = getSummonerIdByPuuid(puuid, shard);
        if (summonerId != 0) {
            RedisClient.delete(RedisKey.PROFILE_RANK.of(summonerId));
            RedisClient.delete(RedisKey.PROFILE_RECENT_MATCHES.of(summonerId, PROFILE_DEFAULT_RECENT_LIMIT));
            RedisClient.delete(RedisKey.PROFILE_TOP_CHAMPIONS.of(summonerId, PROFILE_DEFAULT_TOP_CHAMPIONS_LIMIT));
        }
    }

    public static List<SummonerSearchResult> searchSummoners(String query, LeagueShard shard) {
        String normalizedQuery = normalizeSearch(query);
        String key = RedisKey.SUMMONER_SEARCH.of(shard.name(), normalizedQuery);
        List<SummonerSearchResult> cached = RedisClient.get(key, SUMMONER_SEARCH_TYPE);
        if (cached != null) return cached;

        List<SummonerSearchResult> summoners = new ArrayList<>();
        for (QueryRecord row : LeagueDB.searchSummoners(normalizedQuery, shard)) {
            SummonerProfile profile = toSummonerProfile(row);
            SummonerRank rank = getProfileRank(profile.summonerId());
            summoners.add(toSummonerSearchResult(profile, rank));
        }
        RedisClient.set(key, summoners, TTL_SUMMONER_SEARCH);
        return summoners;
    }

    public static SummonerProfile getProfileBase(String puuid, LeagueShard shard) {
        String key = RedisKey.PROFILE_BASE.of(shard.name(), puuid);
        SummonerProfile cached = RedisClient.get(key, SummonerProfile.class);
        if (cached != null) return cached;

        QueryRecord row = LeagueDB.getProfileBase(puuid, shard);
        SummonerProfile profile = !row.isEmpty() ? toSummonerProfile(row) : getProfileBaseFromRiot(puuid, shard);
        if (profile != null) RedisClient.set(key, profile, TTL_PROFILE_BASE);
        return profile;
    }

    public static SummonerRank getProfileRank(int summonerId) {
        String key = RedisKey.PROFILE_RANK.of(summonerId);
        SummonerRank cached = RedisClient.get(key, SummonerRank.class);
        if (cached != null) return cached;

        QueryRecord row = LeagueDB.getProfileRank(summonerId);
        SummonerRank rank = !row.isEmpty() ? toSummonerRank(row) : SummonerRank.unranked();
        RedisClient.set(key, rank, TTL_PROFILE_RANK);
        return rank;
    }

    public static SummonerRank getProfileRank(String puuid, LeagueShard shard) {
        String key = RedisKey.PROFILE_RANK.of(shard.name() + ":" + puuid);
        SummonerRank cached = RedisClient.get(key, SummonerRank.class);
        if (cached != null) return cached;

        LeagueEntry entry = getLeagueEntry(puuid, shard, "5v5 Ranked Solo");
        SummonerRank rank = entry != null
            ? new SummonerRank(entry.getTierDivisionType().name(), entry.getLeaguePoints(), entry.getWins(), entry.getLosses())
            : SummonerRank.unranked();
        RedisClient.set(key, rank, TTL_PROFILE_RANK);
        return rank;
    }

    public static List<ProfileMatch> getProfileRecentMatches(int summonerId, int limit) {
        String key = RedisKey.PROFILE_RECENT_MATCHES.of(summonerId, limit);
        List<ProfileMatch> cached = RedisClient.get(key, PROFILE_MATCHES_TYPE);
        if (cached != null) return cached;

        List<ProfileMatch> matches = new ArrayList<>();
        for (QueryRecord row : LeagueDB.getProfileRecentMatches(summonerId, limit)) {
            matches.add(toProfileMatch(row));
        }
        RedisClient.set(key, matches, TTL_PROFILE_RECENT_MATCHES);
        return matches;
    }

    public static List<ProfileChampionStats> getProfileTopChampions(int summonerId, int limit) {
        String key = RedisKey.PROFILE_TOP_CHAMPIONS.of(summonerId, limit);
        List<ProfileChampionStats> cached = RedisClient.get(key, PROFILE_CHAMPION_STATS_TYPE);
        if (cached != null) return cached;

        List<ProfileChampionStats> champions = new ArrayList<>();
        for (QueryRecord row : LeagueDB.getProfileTopChampions(summonerId, limit)) {
            champions.add(toProfileChampionStats(row));
        }
        RedisClient.set(key, champions, TTL_PROFILE_TOP_CHAMPIONS);
        return champions;
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

    public static List<String> getMatchList(Summoner summoner, GameQueueType queue, int index) {
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

    public static String putMatch(LOLMatch match) {
      String gameId = match.getPlatform().name() + "_" + match.getGameId();
      RegionShard region = match.getPlatform().toRegionShard();
      String key = RedisKey.MATCH.of(region.name(), gameId);
      RedisClient.set(key, match, TTL_MATCH);
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
    
        String normalizedQuery = query.trim().toLowerCase();
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
    
    private static List<Choice> toChoices(List<SummonerAutocompleteChoice> autocompleteChoices) {
        List<Choice> choices = new ArrayList<>();
    
        for (SummonerAutocompleteChoice choice : autocompleteChoices) {
            choices.add(new Choice(choice.riotId(), choice.puuid()));
        }
    
        return choices;
    }

    private static SummonerSearchResult toSummonerSearchResult(SummonerProfile profile, SummonerRank rank) {
        return new SummonerSearchResult(
            profile.puuid(),
            profile.riotId(),
            profile.region(),
            rank.rank(),
            rank.lp(),
            rank.wins(),
            rank.losses()
        );
    }

    private static SummonerProfile toSummonerProfile(QueryRecord row) {
        return new SummonerProfile(
            row.getAsInt("summoner_id"),
            row.get("puuid"),
            row.get("riot_id"),
            row.get("region"),
            row.getAsInt("level"),
            row.getAsInt("icon")
        );
    }

    private static SummonerProfile getProfileBaseFromRiot(String puuid, LeagueShard shard) {
        Summoner summoner = getSummonerByPuuid(puuid, shard);
        if (summoner == null) return null;

        RiotAccount account = getRiotAccountByPuuid(puuid, shard);
        return new SummonerProfile(
            0,
            puuid,
            account != null ? account.getName() + "#" + account.getTag() : "",
            shard.name(),
            summoner.getSummonerLevel(),
            summoner.getProfileIconId()
        );
    }

    private static SummonerRank toSummonerRank(QueryRecord row) {
        return new SummonerRank(
            row.getOrDefault("rank", "UNRANKED"),
            row.getAsInt("lp"),
            row.getAsInt("wins"),
            row.getAsInt("losses")
        );
    }

    private static ProfileChampionStats toProfileChampionStats(QueryRecord row) {
        return new ProfileChampionStats(
            row.getAsInt("champion"),
            row.getAsInt("games"),
            row.getAsInt("wins"),
            row.getAsInt("losses"),
            row.getAsDouble("avg_kills"),
            row.getAsDouble("avg_deaths"),
            row.getAsDouble("avg_assists"),
            row.getAsDouble("avg_cs"),
            (int) Math.round(row.getAsDouble("avg_damage")),
            row.getAsInt("mastery_level"),
            row.getAsInt("mastery_points")
        );
    }

    private static ProfileMatch toProfileMatch(QueryRecord row) {
        return new ProfileMatch(
            row.get("game_id"),
            row.get("queue"),
            timeMs(row.get("time_start")),
            timeMs(row.get("time_end")),
            row.getAsBoolean("win"),
            row.get("kda"),
            row.getAsInt("champion"),
            row.get("lane"),
            row.getAsInt("damage"),
            row.getAsInt("cs"),
            row.getAsInt("gold_earned"),
            row.getAsInt("vision_score"),
            items(row),
            summonerSpells(row)
        );
    }

    private static List<Integer> items(QueryRecord row) {
        JSONObject items = build(row).optJSONObject("items");
        if (items == null) return List.of();

        return List.of(
            items.optInt("0", 0),
            items.optInt("1", 0),
            items.optInt("2", 0),
            items.optInt("3", 0),
            items.optInt("4", 0),
            items.optInt("5", 0),
            items.optInt("6", 0)
        );
    }

    private static List<Integer> summonerSpells(QueryRecord row) {
        JSONArray spells = build(row).optJSONArray("summoner_spells");
        if (spells == null) return List.of();

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

    private static String normalizeSearch(String query) {
        return query == null ? "" : query.trim().toLowerCase().replace(" ", "");
    }
}
