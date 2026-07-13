package com.safjnest.lol.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;

/** Complete, single-row profile aggregate. All grouping references are R4J enums. */
public class ProfileStatistics {
    public long timeStart;
    public long timeEnd;
    public Stats<Void> total = new Stats<>();
    public List<Stats<GameQueueType>> queueStats = new ArrayList<>();
    public List<Stats<LaneType>> laneStats = new ArrayList<>();
    public List<Stats<Integer>> championStats = new ArrayList<>();
    public List<ProfileMatch> recentMatches = new ArrayList<>();

    public ProfileStatistics() {}

    public ProfileStatistics(long timeStart) {
        this.timeStart = timeStart;
        this.timeEnd = timeStart;
    }

    public void add(ProfileMatch match, GameQueueType queue, LaneType lane) {
        total.add(match);
        stat(queueStats, queue).add(match);
        if (lane != null && lane != LaneType.NONE) stat(laneStats, lane).add(match);
        stat(championStats, match.championId()).add(match);
        timeEnd = Math.max(timeEnd, match.timeEnd());
        recentMatches.add(copy(match));
        recentMatches.sort(Comparator.comparingLong(ProfileMatch::timeStart).reversed());
        if (recentMatches.size() > 5) recentMatches = new ArrayList<>(recentMatches.subList(0, 5));
    }

    private static <T> Stats<T> stat(List<Stats<T>> stats, T reference) {
        for (Stats<T> value : stats) {
            if (value.reference.equals(reference)) return value;
        }
        Stats<T> value = new Stats<>(reference);
        stats.add(value);
        return value;
    }

    private static ProfileMatch copy(ProfileMatch match) {
        return new ProfileMatch(
            match.gameId(), match.queue(), match.timeStart(), match.timeEnd(), match.win(), match.kda(), match.championId(),
            match.lane(), match.damage(), match.cs(), match.gold(), match.vision(), match.teamKills(),
            new ArrayList<>(match.items()), new ArrayList<>(match.summonerSpells()), new ArrayList<>(match.participants())
        );
    }
}
