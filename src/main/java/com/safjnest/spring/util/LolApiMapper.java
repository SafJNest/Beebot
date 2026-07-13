package com.safjnest.spring.util;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.safjnest.lol.LeagueHandler;
import com.safjnest.lol.model.ProfileChampion;
import com.safjnest.lol.model.ProfileMastery;
import com.safjnest.lol.model.ProfileMatch;
import com.safjnest.lol.model.ProfileMatchParticipant;
import com.safjnest.lol.model.ProfilePageData;
import com.safjnest.lol.model.ProfileStatistics;
import com.safjnest.lol.model.Stats;
import com.safjnest.lol.model.SummonerRank;
import com.safjnest.lol.model.SummonerSearchResult;
import com.safjnest.lol.utils.LaneTypeUtils;
import com.safjnest.spring.dto.LolProfileView;
import com.safjnest.spring.dto.LolSearchResult;

import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;

/** Stateless HTTP mapping. Data loading belongs to the lol services. */
public class LolApiMapper {

    private static final int TOP_LIMIT = 5;

    public static LolSearchResult toSearchResult(SummonerSearchResult result) {
        RiotId riotId = RiotId.parse(result.riotId());
        int totalGames = result.wins() + result.losses();
        return new LolSearchResult(result.puuid(), result.riotId(), riotId.name(), riotId.tag(), result.region(),
            result.rank(), result.lp(), result.wins(), result.losses(), rounded(ratio(result.wins(), totalGames) * 100));
    }

    public static LolProfileView toProfileView(ProfilePageData page) {
        ProfileStatistics statistics = page.statistics();
        List<LolProfileView.RoleStat> roles = roles(statistics);
        return new LolProfileView(
            profile(page), summary(statistics, roles), roles, queues(statistics), champions(statistics, page.masteries(), page.champions()),
            recentMatches(statistics.recentMatches, page.champions())
        );
    }

    public static String duration(long milliseconds) {
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        long remainingSeconds = seconds % 60;
        return minutes + ":" + String.format("%02d", remainingSeconds);
    }

    public static String ago(long timeStart) {
        long minutes = Duration.between(Instant.ofEpochMilli(timeStart), Instant.now()).toMinutes();
        if (minutes < 60) return minutes + "m";
        long hours = minutes / 60;
        if (hours < 24) return hours + "h";
        return (hours / 24) + "d";
    }

    public static double ratio(int part, int total) {
        return total > 0 ? (double) part / total : 0;
    }

    public static double rounded(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static LolProfileView.Profile profile(ProfilePageData page) {
        RiotId riotId = RiotId.parse(page.profile().riotId());
        return new LolProfileView.Profile(
            page.profile().puuid(), page.profile().riotId(), riotId.name(), riotId.tag(), page.profile().region(), page.profile().level(),
            page.profile().icon(), LeagueHandler.getSummonerProfilePic(page.profile().icon()), rankEntries(page.ranks())
        );
    }

    private static List<LolProfileView.RankEntry> rankEntries(List<SummonerRank> ranks) {
        List<LolProfileView.RankEntry> response = new ArrayList<>();
        for (SummonerRank rank : ranks) {
            int totalGames = rank.wins() + rank.losses();
            response.add(new LolProfileView.RankEntry(rank.queue().name(), rank.rank().name(), rank.lp(), rank.wins(), rank.losses(),
                rounded(ratio(rank.wins(), totalGames) * 100)));
        }
        return response;
    }

    private static LolProfileView.Summary summary(ProfileStatistics statistics, List<LolProfileView.RoleStat> roles) {
        StringBuilder form = new StringBuilder();
        for (ProfileMatch match : statistics.recentMatches) form.append(match.win() ? 'W' : 'L');
        String mainRole = roles.stream().filter(role -> role.games() > 0).map(LolProfileView.RoleStat::role).findFirst().orElse(null);
        Stats<Void> total = statistics.total;
        return new LolProfileView.Summary(form.toString(), mainRole, total.kda, integer(total.avgDamage), integer(total.games), total.playtime,
            total.lastPlayedAt == 0 ? null : total.lastPlayedAt, total.avgVision, total.avgKillParticipation);
    }

    private static List<LolProfileView.RoleStat> roles(ProfileStatistics statistics) {
        List<Stats<LaneType>> values = new ArrayList<>();
        for (LaneType lane : LaneTypeUtils.playables()) values.add(stat(statistics.laneStats, lane));
        long totalGames = values.stream().mapToLong(value -> value.games).sum();
        values.sort(Comparator.comparingLong((Stats<LaneType> value) -> value.games).reversed()
            .thenComparingInt(value -> LaneTypeUtils.playableOrder(value.reference)));

        List<LolProfileView.RoleStat> response = new ArrayList<>();
        for (Stats<LaneType> value : values) {
            response.add(new LolProfileView.RoleStat(LaneTypeUtils.apiName(value.reference), integer(value.games), percent(value.games, totalGames),
                integer(value.wins), integer(value.losses()), value.winrate, value.kda, integer(value.avgDamage), value.avgVision, value.avgCs,
                value.avgKillParticipation));
        }
        return response;
    }

    private static List<LolProfileView.QueueStatistic> queues(ProfileStatistics statistics) {
        List<Stats<GameQueueType>> values = new ArrayList<>(statistics.queueStats);
        values.sort(Comparator.comparingLong((Stats<GameQueueType> value) -> value.games).reversed().thenComparing(value -> value.reference.name()));
        List<LolProfileView.QueueStatistic> response = new ArrayList<>();
        for (int i = 0; i < values.size() && i < TOP_LIMIT; i++) {
            Stats<GameQueueType> value = values.get(i);
            response.add(new LolProfileView.QueueStatistic(value.reference.name(), integer(value.games), integer(value.wins), integer(value.losses()),
                value.winrate, value.kda, integer(value.avgDamage), value.avgVision, value.avgCs, value.avgKillParticipation));
        }
        return response;
    }

    private static List<LolProfileView.TopChampion> champions(ProfileStatistics statistics, List<ProfileMastery> masteries,
                                                               Map<Integer, ProfileChampion> champions) {
        Map<Integer, ProfileMastery> masteryByChampion = new HashMap<>();
        for (ProfileMastery mastery : masteries) masteryByChampion.put(mastery.championId(), mastery);

        List<Stats<Integer>> values = new ArrayList<>(statistics.championStats);
        values.sort(Comparator.comparingLong((Stats<Integer> value) -> value.games).reversed().thenComparing(value -> value.reference));
        List<LolProfileView.TopChampion> response = new ArrayList<>();
        for (int i = 0; i < values.size() && i < TOP_LIMIT; i++) {
            Stats<Integer> value = values.get(i);
            ProfileMastery mastery = masteryByChampion.get(value.reference);
            ProfileChampion champion = champion(champions, value.reference);
            response.add(new LolProfileView.TopChampion(value.reference, champion.name(), champion.image(), integer(value.games),
                integer(value.wins), integer(value.losses()), value.winrate, value.avgKills, value.avgDeaths, value.avgAssists, value.kda, value.avgCs,
                integer(value.avgDamage), value.avgVision, value.avgKillParticipation, mastery != null ? mastery.level() : 0,
                mastery != null ? mastery.points() : 0));
        }
        return response;
    }

    private static List<LolProfileView.RecentMatch> recentMatches(List<ProfileMatch> matches, Map<Integer, ProfileChampion> champions) {
        List<LolProfileView.RecentMatch> response = new ArrayList<>();
        for (ProfileMatch match : matches) {
            long duration = Math.max(0, match.timeEnd() - match.timeStart());
            ProfileChampion champion = champion(champions, match.championId());
            response.add(new LolProfileView.RecentMatch(match.gameId(), match.win(), match.win() ? "W" : "L", match.championId(),
                champion.name(), champion.image(), LaneTypeUtils.apiName(match.lane()), match.kda(),
                kdaRatio(match.kda()), match.cs(), match.queue() != null ? match.queue().name() : "UNKNOWN", duration, duration(duration),
                match.timeStart(), ago(match.timeStart()), match.damage(), match.gold(), match.vision(), match.items(), match.summonerSpells(),
                participants(match.participants())));
        }
        return response;
    }

    private static List<LolProfileView.MatchParticipant> participants(List<ProfileMatchParticipant> participants) {
        List<LolProfileView.MatchParticipant> response = new ArrayList<>();
        for (ProfileMatchParticipant participant : participants) {
            response.add(new LolProfileView.MatchParticipant(
                participant.championId(), participant.puuid(), participant.team()
            ));
        }
        return response;
    }

    private static <T> Stats<T> stat(List<Stats<T>> values, T reference) {
        for (Stats<T> value : values) if (value.reference.equals(reference)) return value;
        Stats<T> zero = new Stats<>(reference);
        zero.recalculate();
        return zero;
    }

    private static ProfileChampion champion(Map<Integer, ProfileChampion> champions, int championId) {
        return champions.getOrDefault(championId, new ProfileChampion(String.valueOf(championId), null));
    }

    private static double percent(long part, long total) {
        return total > 0 ? rounded((double) part / total * 100) : 0;
    }

    private static int integer(double value) { return (int) Math.round(value); }
    private static int integer(long value) { return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value; }

    private static double kdaRatio(String kda) {
        String[] parts = kda == null ? new String[0] : kda.split("/");
        if (parts.length != 3) return 0;
        int kills = integer(parts[0]);
        int deaths = integer(parts[1]);
        int assists = integer(parts[2]);
        return deaths > 0 ? rounded((double) (kills + assists) / deaths) : kills + assists;
    }

    private static int integer(String value) {
        try { return Integer.parseInt(value); }
        catch (Exception ignored) { return 0; }
    }

    private record RiotId(String name, String tag) {
        private static RiotId parse(String value) {
            if (value == null || value.isBlank()) return new RiotId("", "");
            String[] parts = value.split("#", 2);
            return new RiotId(parts[0], parts.length > 1 ? parts[1] : "");
        }
    }
}
