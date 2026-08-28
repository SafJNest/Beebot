package com.safjnest.spring.config;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.safjnest.lol.champion.RuneSignature;
import com.safjnest.lol.model.Build;
import com.safjnest.lol.model.ChampionStatistics;
import com.safjnest.lol.model.ChampionView;
import com.safjnest.lol.model.ResponseMetadata;
import com.safjnest.lol.model.match.Match;
import com.safjnest.lol.model.summoner.Summoner;
import com.safjnest.lol.model.summoner.Rank;
import com.safjnest.lol.model.summoner.SummonerView;

import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierDivisionType;

public class LolApiConfigTest {

    @Test
    public void doesNotExposeChampionStatisticsFilter() throws Exception {
        ObjectMapper mapper = apiMapper();

        ChampionStatistics stats = new ChampionStatistics(
            null, 10, 5, 1, 3, 0.6, 0.5, 0.1,
            List.of(new ChampionStatistics.LaneStat(LaneType.UTILITY, 5, 0.6)),
            java.util.Map.of()
        );
        Build build = new Build(
            null,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            new RuneSignature(0, 0, List.of(), 0, List.of(), List.of(0, 0, 0)),
            0,
            0
        );
        String json = mapper.writeValueAsString(new ChampionView(
            new ChampionView.Champion(412, "Thresh", "image"), stats, build
        ));

        assertFalse(json.contains("\"filter\""));
    }

    @Test
    public void serializesSummonerFieldsWithoutDirtyState() throws Exception {
        ObjectMapper mapper = apiMapper();
        Summoner summoner = new Summoner("puuid-42", "Name#TAG", no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard.EUW1, 500, 1234);

        String summonerJson = mapper.writeValueAsString(summoner);
        String viewJson = mapper.writeValueAsString(SummonerView.from(summoner, Map.of(), null, List.of()));

        assertFalse(summonerJson.contains("\"summonerId\""));
        assertTrue(summonerJson.contains("\"puuid\":\"puuid-42\""));
        assertTrue(summonerJson.contains("\"riotId\":\"Name#TAG\""));
        assertTrue(summonerJson.contains("\"level\":500"));
        assertTrue(summonerJson.contains("\"icon\":1234"));
        assertTrue(viewJson.contains("\"summoner\":{"));
        assertTrue(viewJson.contains("\"region\":\"EUW1\""));
        assertTrue(viewJson.contains("\"ranks\":{}"));
        assertFalse(summonerJson.contains("\"dirty\""));
        assertFalse(viewJson.contains("\"dirty\""));
    }

    @Test
    public void serializesRanksAsCanonicalQueueObjectWithoutEmbeddedQueue() throws Exception {
        ObjectMapper mapper = apiMapper();
        Summoner summoner = new Summoner("puuid-42", "Name#TAG", no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard.EUW1, 500, 1234);

        String json = mapper.writeValueAsString(SummonerView.from(summoner,
            Map.of(GameQueueType.RANKED_SOLO_5X5, new Rank(TierDivisionType.MASTER_I, 500, 10, 5)), null, List.of()));

        assertTrue(json.contains("\"ranks\":{\"RANKED_SOLO_5X5\":{"));
        assertFalse(json.contains("\"ranks\":["));
        assertFalse(json.contains("\"queue\":"));
    }

    @Test
    public void serializesMatchWithoutDirtyState() throws Exception {
        ObjectMapper mapper = apiMapper();
        Match match = Match.hydrated();
        match.gameId = "EUW1_123";
        match.leagueShard = no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard.EUW1;

        String json = mapper.writeValueAsString(match);

        assertTrue(json.contains("\"gameId\":\"EUW1_123\""));
        assertFalse(json.contains("\"dirty\""));
    }

    @Test
    public void serializesRootMetadataWithoutDataEnvelope() throws Exception {
        ObjectMapper mapper = apiMapper();
        ChampionView view = new ChampionView(null, null, null,
            new ResponseMetadata(null, null, null, null));

        String json = mapper.writeValueAsString(view);

        assertTrue(json.contains("\"metadata\":{\"pagination\":null"));
        assertTrue(json.contains("\"lastUpdate\":null"));
        assertTrue(json.contains("\"refresh\":null"));
        assertTrue(json.contains("\"filter\":null"));
        assertFalse(json.contains("\"data\":"));
    }

    private static ObjectMapper apiMapper() {
        List<HttpMessageConverter<?>> converters = new ArrayList<>();
        new LolApiConfig().configureMessageConverters(converters);
        return ((MappingJackson2HttpMessageConverter) converters.get(0)).getObjectMapper();
    }
}
