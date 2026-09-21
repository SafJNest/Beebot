package com.safjnest.lol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.safjnest.lol.model.summoner.Rank;
import com.safjnest.lol.model.summoner.Summoner;
import com.safjnest.nosql.MongoDB;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.TierDivisionType;

public class SummonerServiceTest {

    @Test
    void sortsPrefixMatchesBySoloMmrWithUnrankedLast() {
        List<MongoDB.SummonerSearchResult> rows = new ArrayList<>(List.of(
            row("unranked", "Adam#EUW", null),
            row("gold", "Zed#EUW", new Rank(TierDivisionType.GOLD_I, 99, 1, 1)),
            row("emerald-low", "Aatrox#EUW", new Rank(TierDivisionType.EMERALD_II, 0, 1, 1)),
            row("emerald-high", "Ahri#EUW", new Rank(TierDivisionType.EMERALD_II, 99, 1, 1)),
            row("unranked-tie", "Aatrox#EUW", Rank.unranked())
        ));

        SummonerService.sortSearchResults(rows);

        List<String> puuids = new ArrayList<>();
        for (MongoDB.SummonerSearchResult row : rows) puuids.add(row.summoner().puuid());

        assertEquals(List.of("emerald-high", "emerald-low", "gold", "unranked-tie", "unranked"), puuids);
    }

    private static MongoDB.SummonerSearchResult row(String puuid, String riotId, Rank rank) {
        return new MongoDB.SummonerSearchResult(new Summoner(puuid, riotId, LeagueShard.EUW1, 1, 1), rank);
    }
}
