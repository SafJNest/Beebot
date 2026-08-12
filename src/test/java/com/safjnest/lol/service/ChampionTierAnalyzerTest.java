package com.safjnest.lol.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.safjnest.lol.model.ChampionTierList;
import com.safjnest.lol.model.ChampionTierSource;
import com.safjnest.lol.model.ChampionView;
import com.safjnest.lol.model.Filter;

import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;

public class ChampionTierAnalyzerTest {

    @Test
    public void clustersChampionRolesUsingPicksAndRoleShare() {
        Filter top = filter(LaneType.TOP);
        Filter mid = filter(LaneType.MID);
        Filter bot = filter(LaneType.BOT);
        Filter utility = filter(LaneType.UTILITY);
        Map<Integer, ChampionTierSource.Champion> topChampions = new LinkedHashMap<>();
        topChampions.put(1, champion(1, 2, 0.0002, 0.01, List.of()));
        topChampions.put(2, champion(230, 460, 0.046, 0.01, List.of()));
        topChampions.put(4, champion(2, 3, 0.0003, 0.01, List.of()));
        Map<Integer, ChampionTierSource.Champion> midChampions = new LinkedHashMap<>();
        midChampions.put(2, champion(235, 470, 0.047, 0.01, List.of()));
        midChampions.put(3, champion(10, 20, 0.002, 0.01, List.of()));
        Map<Integer, ChampionTierSource.Champion> botChampions = new LinkedHashMap<>();
        botChampions.put(3, champion(190, 380, 0.038, 0.01, List.of()));
        Map<Integer, ChampionTierSource.Champion> utilityChampions = new LinkedHashMap<>();
        utilityChampions.put(1, champion(499, 998, 0.0998, 0.01, List.of()));
        utilityChampions.put(2, champion(35, 70, 0.007, 0.01, List.of()));
        utilityChampions.put(3, champion(300, 600, 0.060, 0.01, List.of()));

        Map<String, ChampionTierSource> sources = new LinkedHashMap<>();
        sources.put(top.genericKey(), source(topChampions));
        sources.put(mid.genericKey(), source(midChampions));
        sources.put(bot.genericKey(), source(botChampions));
        sources.put(utility.genericKey(), source(utilityChampions));
        List<ChampionTierList.Role> roles = ChampionTierAnalyzer.analyze(List.of(top, mid, bot, utility),
            sources, this::champion);

        assertFalse(hasChampion(role(roles, LaneType.TOP), 1));
        assertFalse(hasChampion(role(roles, LaneType.TOP), 4));
        assertTrue(hasChampion(role(roles, LaneType.UTILITY), 1));
        assertTrue(hasChampion(role(roles, LaneType.TOP), 2));
        assertTrue(hasChampion(role(roles, LaneType.MID), 2));
        assertFalse(hasChampion(role(roles, LaneType.UTILITY), 2));
        assertTrue(hasChampion(role(roles, LaneType.BOT), 3));
        assertTrue(hasChampion(role(roles, LaneType.UTILITY), 3));
        assertFalse(hasChampion(role(roles, LaneType.MID), 3));
    }

    @Test
    public void shrinksOneGameWinrateBeforeScoringTheTier() {
        Filter filter = filter();
        Map<Integer, ChampionTierSource.Champion> champions = new LinkedHashMap<>();
        champions.put(1, champion(1, 1, 0.10, 0.01, List.of()));
        for (int id = 2; id <= 6; id++) champions.put(id, champion(50, 100, 0.10, 0.01, List.of()));

        ChampionTierList.Role role = role(filter, champions);
        ChampionTierList.Champion oneGame = champion(role, 1);

        assertFalse("S".equals(oneGame.tier()));
        assertFalse("S+".equals(oneGame.tier()));
    }

    @Test
    public void ignoresZeroDeviationComponents() {
        Filter filter = filter();
        Map<Integer, ChampionTierSource.Champion> champions = new LinkedHashMap<>();
        champions.put(1, champion(50, 100, 0.10, 0.10, List.of()));
        champions.put(2, champion(50, 100, 0.10, 0.10, List.of()));

        ChampionTierList.Role role = role(filter, champions);

        assertEquals(0d, role.champions().get(0).tierScore(), 0d);
        assertEquals("B", role.champions().get(0).tier());
        assertEquals(1, role.champions().get(0).champion().id());
    }

    @Test
    public void removesLowSampleMatchupsBeforeRankingCounters() {
        Filter filter = filter();
        Map<Integer, ChampionTierSource.Champion> champions = new LinkedHashMap<>();
        champions.put(1, champion(60, 100, 0.20, 0.20, List.of(
            new ChampionTierSource.Matchup(2, 4, 0),
            new ChampionTierSource.Matchup(3, 5, 0),
            new ChampionTierSource.Matchup(4, 7, 0),
            new ChampionTierSource.Matchup(5, 9, 3),
            new ChampionTierSource.Matchup(6, 13, 4),
            new ChampionTierSource.Matchup(7, 27, 24),
            new ChampionTierSource.Matchup(8, 30, 25)
        )));
        for (int id = 2; id <= 8; id++) champions.put(id, champion(50, 100, 0.10, 0.10, List.of()));

        ChampionTierList.Champion champion = champion(role(filter, champions), 1);

        assertEquals(6, champion.counters().get(0).champion().id());
        assertEquals(9, champion.counters().get(0).losses());
        assertEquals(2, champion.counters().size());
        assertEquals(5, champion.counters().get(1).champion().id());
        assertEquals(7, champion.strongAgainst().get(0).champion().id());
        assertEquals(2, champion.strongAgainst().size());
    }

    @Test
    public void derivesMatchupPriorOnlyFromEligibleOpponentsInTheSameRole() {
        Filter top = filter(LaneType.TOP);
        Filter mid = filter(LaneType.MID);
        Map<Integer, ChampionTierSource.Champion> topChampions = new LinkedHashMap<>();
        topChampions.put(1, champion(600, 1_000, 0.10, 0.01, List.of(
            new ChampionTierSource.Matchup(2, 20, 8),
            new ChampionTierSource.Matchup(3, 200, 80)
        )));
        topChampions.put(2, champion(350, 700, 0.07, 0.01, List.of()));
        topChampions.put(3, champion(5, 10, 0.001, 0.01, List.of()));
        Map<Integer, ChampionTierSource.Champion> midChampions = new LinkedHashMap<>();
        midChampions.put(3, champion(250, 500, 0.05, 0.01, List.of()));
        Map<String, ChampionTierSource> sources = new LinkedHashMap<>();
        sources.put(top.genericKey(), source(topChampions));
        sources.put(mid.genericKey(), source(midChampions));

        ChampionTierList.Champion riven = champion(ChampionTierAnalyzer.analyze(List.of(top, mid), sources,
            this::champion).get(0), 1);

        assertEquals(1, riven.counters().size());
    }

    private boolean hasChampion(ChampionTierList.Role role, int championId) {
        for (ChampionTierList.Champion champion : role.champions())
            if (champion.champion().id() == championId) return true;
        return false;
    }

    private ChampionTierList.Role role(List<ChampionTierList.Role> roles, LaneType lane) {
        for (ChampionTierList.Role role : roles) if (role.role() == lane) return role;
        throw new AssertionError("Role not found: " + lane);
    }

    private ChampionTierList.Champion champion(ChampionTierList.Role role, int championId) {
        for (ChampionTierList.Champion champion : role.champions())
            if (champion.champion().id() == championId) return champion;
        throw new AssertionError("Champion not found: " + championId);
    }

    private ChampionTierList.Role role(Filter filter, Map<Integer, ChampionTierSource.Champion> champions) {
        ChampionTierSource source = source(champions);
        List<ChampionTierList.Role> roles = ChampionTierAnalyzer.analyze(List.of(filter),
            Map.of(filter.genericKey(), source), this::champion);
        return roles.get(0);
    }

    private ChampionTierSource source(Map<Integer, ChampionTierSource.Champion> champions) {
        return new ChampionTierSource(true, System.currentTimeMillis(), champions);
    }

    private ChampionTierSource.Champion champion(
            int wins,
            int picks,
            double pickrate,
            double banrate,
            List<ChampionTierSource.Matchup> matchups) {
        return new ChampionTierSource.Champion(new ChampionTierList.Statistics(picks, picks, 10, wins,
            (double) wins / picks, pickrate, banrate), matchups);
    }

    private Filter filter() {
        return filter(LaneType.TOP);
    }

    private Filter filter(LaneType lane) {
        return new Filter().setChampion(0).setPatch("15.14").setQueue(GameQueueType.TEAM_BUILDER_RANKED_SOLO)
            .setLane(lane).setRank(null).setRegion(null);
    }

    private ChampionView.Champion champion(int id) {
        return new ChampionView.Champion(id, "Champion " + id, "image-" + id);
    }
}
