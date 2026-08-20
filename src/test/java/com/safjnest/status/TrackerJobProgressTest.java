package com.safjnest.status;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

import com.safjnest.lol.model.summoner.Summoner;
import com.safjnest.lol.model.status.JobProgress;
import com.safjnest.lol.model.status.TrackerMetrics;
import com.safjnest.lol.tracker.TrackerJobProgress;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierDivisionType;

public class TrackerJobProgressTest {

    @Test
    public void tracksTrackingProgressWhileRunning() {
        List<Summoner> accounts = List.of(
            Summoner.hydrated("puuid-1", "Alpha#EUW", LeagueShard.EUW1, 100, 1, null, true, null, null),
            Summoner.hydrated("puuid-2", "Beta#EUW", LeagueShard.EUW1, 200, 2, null, true, null, null),
            Summoner.hydrated("puuid-3", "Gamma#EUW", LeagueShard.EUW1, 300, 3, null, true, null, null)
        );
        TrackerJobProgress.beginTracking(accounts);
        TrackerJobProgress.completeTrackingSummoner("puuid-1");
        TrackerJobProgress.completeTrackingSummoner("puuid-2");

        TrackerMetrics.TrackingJob job = TrackerJobProgress.trackingSnapshot(true, true, 1000L, 3L);

        assertEquals("running", job.state());
        assertNotNull(job.progress());
        assertEquals(2, job.progress().current());
        assertEquals(3, job.progress().total());
        assertNotNull(job.summoners());
        assertEquals(3, job.summoners().size());
        assertTrue(job.summoners().get(0).done());
        assertTrue(job.summoners().get(1).done());
        assertEquals("puuid-1", job.summoners().get(0).puuid());
        assertEquals("puuid-3", job.summoners().get(2).puuid());

        TrackerJobProgress.endTracking();
        job = TrackerJobProgress.trackingSnapshot(true, false, 1000L, 3L);
        assertEquals("idle", job.state());
        assertEquals(null, job.progress());
        assertEquals(null, job.summoners());
    }

    @Test
    public void preservesTrackingSummonerOrder() {
        List<Summoner> accounts = List.of(
            Summoner.hydrated("puuid-1", "Alpha#EUW", LeagueShard.EUW1, 100, 1, null, true, null, null),
            Summoner.hydrated("puuid-2", "Beta#EUW", LeagueShard.EUW1, 200, 2, null, true, null, null),
            Summoner.hydrated("puuid-3", "Gamma#EUW", LeagueShard.EUW1, 300, 3, null, true, null, null)
        );
        TrackerJobProgress.beginTracking(accounts);
        TrackerJobProgress.completeTrackingSummoner("puuid-3");

        TrackerMetrics.TrackingJob job = TrackerJobProgress.trackingSnapshot(true, true, 1000L, 3L);

        assertEquals("puuid-1", job.summoners().get(0).puuid());
        assertEquals("puuid-2", job.summoners().get(1).puuid());
        assertEquals("puuid-3", job.summoners().get(2).puuid());
        assertTrue(job.summoners().get(2).done());

        TrackerJobProgress.endTracking();
    }

    @Test
    public void tracksSampleGamesPerRegion() {
        TrackerJobProgress.beginSampleGames(GameQueueType.TEAM_BUILDER_RANKED_SOLO, List.of(LeagueShard.EUW1, LeagueShard.NA1));
        TrackerJobProgress.sampleRegionLoading(LeagueShard.EUW1);
        TrackerJobProgress.sampleRegionTotal(LeagueShard.EUW1, 10);
        TrackerJobProgress.sampleRegionAnalyzed(LeagueShard.EUW1, 4);

        TrackerMetrics.SampleGamesJob running = TrackerJobProgress.sampleGamesSnapshot(null);

        assertEquals("running", running.state());
        assertEquals(GameQueueType.TEAM_BUILDER_RANKED_SOLO.name(), running.queue());
        assertEquals(2, running.regions().size());
        TrackerMetrics.SampleGamesRegion euw1 = running.regions().stream()
            .filter(region -> "EUW1".equals(region.shard()))
            .findFirst()
            .orElseThrow();
        assertEquals(10, euw1.total());
        assertEquals(4, euw1.analyzed());
        assertEquals(6, euw1.remaining());

        TrackerJobProgress.sampleRegionDone(LeagueShard.EUW1);
        TrackerJobProgress.sampleRegionDone(LeagueShard.NA1);

        TrackerMetrics.SampleGamesJob idle = TrackerJobProgress.sampleGamesSnapshot(null);
        assertEquals("idle", idle.state());
        assertEquals(0, idle.regions().size());
    }

    @Test
    public void mapsHighEloContextAndProgress() {
        TrackerJobProgress.beginHighElo(
            List.of(TierDivisionType.MASTER_I, TierDivisionType.GRANDMASTER_I),
            List.of(LeagueShard.EUW1),
            List.of(GameQueueType.RANKED_SOLO_5X5, GameQueueType.RANKED_FLEX_SR)
        );
        TrackerJobProgress.startHighEloStep("MASTER_I", LeagueShard.EUW1, GameQueueType.RANKED_SOLO_5X5);
        TrackerJobProgress.completeHighEloStep("MASTER_I", LeagueShard.EUW1, GameQueueType.RANKED_SOLO_5X5);

        TrackerMetrics.HighEloJob job = TrackerJobProgress.highEloSnapshot(true, true, 2000L);

        assertEquals("running", job.state());
        assertEquals("MASTER_I", job.tier());
        assertEquals("EUW1", job.shard());
        assertEquals("RANKED_SOLO_5X5", job.queue());
        assertEquals(new JobProgress(1, 4), job.progress());
        assertNotNull(job.steps());
        assertEquals(4, job.steps().size());
        assertTrue(job.steps().get(0).done());

        TrackerJobProgress.endHighElo();
        job = TrackerJobProgress.highEloSnapshot(true, false, 2000L);
        assertEquals(null, job.steps());
    }
}
