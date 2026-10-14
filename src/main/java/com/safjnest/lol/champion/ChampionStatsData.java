package com.safjnest.lol.champion;

import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.TeamType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierType;
import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;

import java.util.List;
import java.util.Map;

public final class ChampionStatsData {

    public record RawParticipant(
        int champion,
        LaneType lane,
        boolean win,
        TeamType team,
        String matchId,
        String kda,
        Integer cs,
        Integer gold,
        String puuid,
        int subTeam,
        int subTeamPlacement
    ) {
        public RawParticipant(int champion, LaneType lane, boolean win, TeamType team, String matchId,
                String kda, Integer cs, Integer gold, String puuid, int subTeam) {
            this(champion, lane, win, team, matchId, kda, cs, gold, puuid, subTeam, 0);
        }

        public RawParticipant(int champion, LaneType lane, boolean win, TeamType team, String matchId,
                String kda, Integer cs, Integer gold, String puuid) {
            this(champion, lane, win, team, matchId, kda, cs, gold, puuid, 0, 0);
        }
    }

    public record MatchMeta(
        Map<String, Object> bans,
        Object events,
        long timeStart,
        long timeEnd,
        LeagueShard region,
        TierType rank,
        GameQueueType queue
    ) {
        public MatchMeta(Map<String, Object> bans, Object events, long timeStart, long timeEnd,
                LeagueShard region, TierType rank) {
            this(bans, events, timeStart, timeEnd, region, rank, null);
        }
    }

    public record RawMatch(String matchId, MatchMeta metadata, List<RawParticipant> participants) {}

    public record RawMatchRead(RawMatch match, long matchReadNanos, long eventReadNanos, long materializeNanos) {}

    public record TrendParticipant(int champion, LaneType lane, boolean win) {}

    public record Player(int champion, LaneType lane, boolean win, TeamType team, String matchId,
                         long timeStart, long timeEnd, String kda, Integer cs, Integer gold, String puuid,
                         int subTeam, int subTeamPlacement) {
        public Player(int champion, LaneType lane, boolean win, TeamType team, String matchId,
                long timeStart, long timeEnd, String kda, Integer cs, Integer gold, String puuid, int subTeam) {
            this(champion, lane, win, team, matchId, timeStart, timeEnd, kda, cs, gold, puuid, subTeam, 0);
        }

        public Player(int champion, LaneType lane, boolean win, TeamType team, String matchId,
                long timeStart, long timeEnd, String kda, Integer cs, Integer gold, String puuid) {
            this(champion, lane, win, team, matchId, timeStart, timeEnd, kda, cs, gold, puuid, 0, 0);
        }
    }

    public record Snapshot(Integer cs, Integer gold, Integer xp, Integer level) {}

    public record EventMetric(int kills, int soloKills, int assists, int teamKills, int deaths,
                              int enemyTeamKills, int killsAt15, int deathsAt15, int platesAt15,
                              boolean available) {}

    public record MatchData(Map<String, EventMetric> eventMetrics, Map<String, Snapshot> snapshots,
                            boolean eventsAvailable) {}

    public record Game(String matchId, Map<String, Object> bans, long timeStart, long timeEnd,
                       List<Player> players, MatchData data) {}

    public record MetricValues(Double kda, Double csPerMinute, Double goldPerMinute) {}

    public record SynergyKey(int champion, LaneType lane) {}

    private ChampionStatsData() {}
}
