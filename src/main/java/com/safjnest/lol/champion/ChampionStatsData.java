package com.safjnest.lol.champion;

import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.basic.constants.types.lol.TeamType;

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
        String puuid
    ) {}

    public record MatchMeta(String bans, String events, long timeStart, long timeEnd) {}

    public record RawMatch(String matchId, MatchMeta metadata, List<RawParticipant> participants) {}

    public record RawBatch(Map<String, MatchMeta> metadata, Map<String, List<RawParticipant>> participants) {}

    public record Player(int champion, LaneType lane, boolean win, TeamType team, String matchId,
                         long timeStart, long timeEnd, String kda, Integer cs, Integer gold, String puuid) {}

    public record Snapshot(Integer cs, Integer gold) {}

    public record EventMetric(int kills, int soloKills, int assists, int teamKills, int deaths,
                              boolean available) {}

    public record MatchData(Map<String, EventMetric> eventMetrics, Map<String, Snapshot> snapshots,
                            boolean eventsAvailable) {}

    public record Game(String matchId, String bans, long timeStart, long timeEnd,
                       List<Player> players, MatchData data) {}

    public record MetricValues(Double kda, Double csPerMinute, Double goldPerMinute) {}

    public record SynergyKey(int champion, LaneType lane) {}

    private ChampionStatsData() {}
}
