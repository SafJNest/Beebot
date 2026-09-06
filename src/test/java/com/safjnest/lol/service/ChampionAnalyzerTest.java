package com.safjnest.lol.service;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.safjnest.lol.champion.ChampionStatsData;
import com.safjnest.lol.model.Filter;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.basic.constants.types.lol.TeamType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierType;

public class ChampionAnalyzerTest {

    @Test
    public void greaterOrEqualMatrixBucketIncludesHigherRanks() {
        ChampionStatsData.RawMatch match = rawMatch(LeagueShard.EUW1, TierType.CHALLENGER);
        Filter emerald = filter(LeagueShard.EUW1, TierType.EMERALD);
        Filter challenger = filter(LeagueShard.EUW1, TierType.CHALLENGER);

        assertTrue(ChampionAnalyzer.matchesMatrixFilter(emerald, match));
        assertTrue(ChampionAnalyzer.matchesMatrixFilter(challenger, match));
    }

    @Test
    public void matrixBucketRejectsLowerRanksAndOtherRegions() {
        ChampionStatsData.RawMatch goldMatch = rawMatch(LeagueShard.EUW1, TierType.GOLD);
        Filter diamond = filter(LeagueShard.EUW1, TierType.DIAMOND);
        Filter euw = filter(LeagueShard.EUW1, TierType.EMERALD);
        Filter na = filter(LeagueShard.NA1, TierType.EMERALD);

        assertFalse(ChampionAnalyzer.matchesMatrixFilter(diamond, goldMatch));
        assertFalse(ChampionAnalyzer.matchesMatrixFilter(euw, goldMatch));
        assertFalse(ChampionAnalyzer.matchesMatrixFilter(na, goldMatch));
    }

    @Test
    public void exactRankBucketDoesNotAccumulateHigherRanks() {
        ChampionStatsData.RawMatch match = rawMatch(LeagueShard.EUW1, TierType.CHALLENGER);
        Filter exactMaster = filter(LeagueShard.EUW1, TierType.MASTER)
            .setRankBehavior(Filter.RankBehavior.EXACT);

        assertFalse(ChampionAnalyzer.matchesMatrixFilter(exactMaster, match));
    }

    @Test
    public void rawBucketsRollUpRankAndRegionWithoutChampionIdCollisions() {
        ChampionAnalyzer.RawMatrix raw = new ChampionAnalyzer.RawMatrix();
        raw.addBase(game("EUW_GOLD", true), metadata(LeagueShard.EUW1, TierType.GOLD));
        raw.addBase(game("EUW_MASTER", false), metadata(LeagueShard.EUW1, TierType.MASTER));
        raw.addBase(game("NA_MASTER", true), metadata(LeagueShard.NA1, TierType.MASTER));

        ChampionAnalyzer.RawProjection globalGold = raw.project(filter(null, TierType.GOLD));
        ChampionAnalyzer.RawProjection euwGold = raw.project(filter(LeagueShard.EUW1, TierType.GOLD));
        ChampionAnalyzer.RawProjection euwDiamond = raw.project(filter(LeagueShard.EUW1, TierType.DIAMOND));

        assertEquals(3, globalGold.totalGames());
        assertEquals(3, globalGold.pickWin().get(10)[0]);
        assertEquals(2, euwGold.totalGames());
        assertEquals(2, euwGold.pickWin().get(10)[0]);
        assertEquals(1, euwDiamond.totalGames());
        assertEquals(1, euwDiamond.pickWin().get(10)[0]);
        assertTrue(globalGold.matchupRaw().get(10).containsKey(999_999));
        assertTrue(globalGold.synergyRaw().get(20).keySet().stream()
            .anyMatch(value -> value.champion() == 30 && value.lane() == LaneType.UTILITY));
    }

    @Test
    public void rawBucketsKeepNullLaneAndRealLaneSeparate() {
        ChampionAnalyzer.RawMatrix raw = new ChampionAnalyzer.RawMatrix();
        ChampionStatsData.Game game = new ChampionStatsData.Game("EUW_NULL", Map.of(), 0, 600_000,
            List.of(
                player(10, LaneType.TOP, true, TeamType.BLUE),
                player(999_999, LaneType.TOP, false, TeamType.RED),
                player(777_777, null, true, TeamType.BLUE)),
            new ChampionStatsData.MatchData(Map.of(), Map.of(), false));
        raw.addBase(game, metadata(LeagueShard.EUW1, TierType.EMERALD));

        ChampionAnalyzer.RawProjection allLanes = raw.project(filter(LeagueShard.EUW1, TierType.EMERALD));
        ChampionAnalyzer.RawProjection top = raw.project(filter(LeagueShard.EUW1, TierType.EMERALD)
            .setLane(LaneType.TOP));

        assertEquals(1, allLanes.pickWin().get(777_777)[0]);
        assertFalse(top.pickWin().containsKey(777_777));
        assertEquals(1, top.pickWin().get(10)[0]);
    }

    private static Filter filter(LeagueShard region, TierType rank) {
        return new Filter()
            .setPatch("15.14")
            .setQueue(GameQueueType.TEAM_BUILDER_RANKED_SOLO)
            .setRegion(region)
            .setRank(rank);
    }

    private static ChampionStatsData.RawMatch rawMatch(LeagueShard region, TierType rank) {
        return new ChampionStatsData.RawMatch(
            "EUW1_1",
            new ChampionStatsData.MatchMeta(Map.of(), Map.of(), 1, 2, region, rank),
            List.of());
    }

    private static ChampionStatsData.MatchMeta metadata(LeagueShard region, TierType rank) {
        return new ChampionStatsData.MatchMeta(Map.of(), null, 0, 600_000, region, rank);
    }

    private static ChampionStatsData.Game game(String id, boolean win) {
        return new ChampionStatsData.Game(id, Map.of(), 0, 600_000,
            List.of(
                player(10, LaneType.TOP, win, TeamType.BLUE),
                player(999_999, LaneType.TOP, !win, TeamType.RED),
                player(20, LaneType.BOT, win, TeamType.BLUE),
                player(30, LaneType.UTILITY, win, TeamType.BLUE),
                player(40, LaneType.BOT, !win, TeamType.RED),
                player(50, LaneType.UTILITY, !win, TeamType.RED)),
            new ChampionStatsData.MatchData(Map.of(), Map.of(), false));
    }

    private static ChampionStatsData.Player player(int champion, LaneType lane, boolean win, TeamType team) {
        return new ChampionStatsData.Player(champion, lane, win, team, "match", 0, 600_000,
            "1/2/3", 100, 10_000, String.valueOf(champion));
    }
}
