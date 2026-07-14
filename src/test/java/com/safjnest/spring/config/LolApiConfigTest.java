package com.safjnest.spring.config;

import static org.junit.Assert.assertFalse;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.safjnest.lol.build.RuneSignature;
import com.safjnest.lol.model.Build;
import com.safjnest.lol.model.ChampionStatistics;
import com.safjnest.lol.model.ChampionView;

import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;

public class LolApiConfigTest {

    @Test
    public void doesNotExposeChampionStatisticsFilter() throws Exception {
        List<HttpMessageConverter<?>> converters = new ArrayList<>();
        new LolApiConfig().configureMessageConverters(converters);
        ObjectMapper mapper = ((MappingJackson2HttpMessageConverter) converters.get(0)).getObjectMapper();

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
}
