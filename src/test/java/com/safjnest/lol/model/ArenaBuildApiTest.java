package com.safjnest.lol.model;

import static org.junit.Assert.*;
import static com.safjnest.lol.arena.ArenaGameParserTest.*;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import com.safjnest.lol.service.ArenaChampionAnalyzer;
import com.safjnest.utils.JsonCodec;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;

public class ArenaBuildApiTest {
    @Test
    public void majorPatchCollectsFullPatchesAndExposesBootsPrismaticCoreWithoutStarters() {
        Filter filter = Filter.championBuild(27, "16.19", GameQueueType.CHERRY);
        var accumulator = ArenaChampionAnalyzer.accumulator(27, filter.patch(), CATALOG);
        var participant = participant(1, 3);
        participant.boots = 3006;
        participant.item0 = 3006; participant.item1 = 447001; participant.item2 = 4001;
        participant.augments = Arrays.asList(11, null, 33, 44, 55, 66, 77);
        var first = match("EUW1_1", participant, List.of(
            event("ITEM_PURCHASED", 1000, 447001), event("ITEM_PURCHASED", 2000, 4001)));
        var second = match("EUW1_2", participant, List.of(event("ITEM_PURCHASED", 1000, 447001)));
        second.patch = "16.19.2";
        assertTrue(accumulator.accept(first));
        assertTrue(accumulator.accept(second));
        Build build = Build.fromArena(filter, accumulator.finish());
        assertEquals(2, build.games());
        assertEquals(2, build.wins());
        assertEquals(List.of(3006, 447001), build.coreBuilds().get(0).items());
        assertTrue(build.starterOptions().isEmpty());
        assertEquals(6, build.augmentOptions().size());
        assertTrue(build.augmentOptions().get(1).isEmpty());
        assertEquals("55", build.augmentOptions().get(4).get(0).id());
        assertEquals("66", build.augmentOptions().get(5).get(0).id());
        assertTrue(build.arena().coreBuilds().get(0).builds().get(0).items().contains(4001));
        Build decoded = JsonCodec.fromJson(build.toJson(), Build.class);
        assertEquals(build.arena(), decoded.arena());
        assertEquals(build.augmentOptions(), decoded.augmentOptions());
    }

    @Test
    public void missingTimelineAndCoreStillExposeGamesPrismaticsAndSixthAugment() {
        var participant = participant(1, 3);
        participant.item0 = 447001;
        participant.augments = List.of(11, 22, 33, 44, 55, 66);
        var source = match("EUW1_3", participant, List.of());
        source.eventData = null;
        var accumulator = ArenaChampionAnalyzer.accumulator(27, "16.19", CATALOG);
        assertTrue(accumulator.accept(source));
        Build build = Build.fromArena(Filter.championBuild(27, "16.19", GameQueueType.CHERRY), accumulator.finish());
        assertEquals(1, build.games());
        assertTrue(build.coreBuilds().isEmpty());
        assertEquals("447001", build.prismaticOptions().get(0).id());
        assertEquals("66", build.augmentOptions().get(5).get(0).id());
    }
    @Test
    public void missingCoreRetainsPopulationWithoutInventingFullBuilds() {
        var participant = participant(1, 3);
        var source = match("EUW1_4", participant, List.of(event("ITEM_PURCHASED", 1000, 4001)));
        var accumulator = ArenaChampionAnalyzer.accumulator(27, "16.19", CATALOG);
        assertTrue(accumulator.accept(source));
        Build build = Build.fromArena(Filter.championBuild(27, "16.19", GameQueueType.CHERRY), accumulator.finish());
        assertTrue(build.coreBuilds().isEmpty());
        assertEquals(1, build.games());
        assertTrue(build.arena().coreBuilds().isEmpty());
        assertEquals(1, build.arena().coverage().missingCoreGames());
    }

}
