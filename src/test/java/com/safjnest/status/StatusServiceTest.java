package com.safjnest.status;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.safjnest.lol.model.status.BotStatus;
import com.safjnest.lol.model.status.JobProgress;
import com.safjnest.lol.model.status.JvmMetrics;
import com.safjnest.lol.model.status.LeagueMetrics;
import com.safjnest.lol.model.status.QueueWorkerStatus;
import com.safjnest.lol.model.status.RedisMetrics;
import com.safjnest.lol.model.status.SystemMetrics;
import com.safjnest.lol.model.status.RiotMetrics;
import com.safjnest.lol.model.status.TrackerMetrics;
import com.safjnest.lol.model.status.WorkerMetrics;

public class StatusServiceTest {

    @Test
    public void currentReturnsOnlineWithLeagueCountersWhenSamplerIsIdle() {
        BotStatus status = new StatusService().current();

        assertEquals(BotStatus.ONLINE, status.status());
        assertTrue(status.league().gameQueue() >= 0);
        assertTrue(status.league().profileQueue() >= 0);
        assertTrue(status.league().gamesAnalyzed() >= 0);
        assertTrue(status.league().totalSummoners() >= 0);
        assertTrue(status.league().totalMasteries() >= 0);
        assertNotNull(status.league().ranksByQueue());
        assertNotNull(status.tracker());
        assertNotNull(status.workers());
        assertNotNull(status.riot());
    }

    private static TrackerMetrics sampleTracker() {
        return new TrackerMetrics(
            "running",
            new TrackerMetrics.TrackingJob("running", 1755680400000L, 142L, new JobProgress(37, 142), List.of(
                new TrackerMetrics.TrackingSummoner("puuid-1", "Alpha#EUW", "EUW1", true),
                new TrackerMetrics.TrackingSummoner("puuid-2", "Beta#EUW", "EUW1", false)
            )),
            new TrackerMetrics.HighEloJob("running", 1755684000000L, "MASTER_I", "EUW1", "RANKED_SOLO_5X5", new JobProgress(12, 84), List.of(
                new TrackerMetrics.HighEloStep("MASTER_I", "EUW1", "RANKED_SOLO_5X5", true),
                new TrackerMetrics.HighEloStep("MASTER_I", "EUW1", "RANKED_FLEX_SR", false)
            )),
            new TrackerMetrics.GameAnalysisJob("idle", 1755648000000L, null),
            new TrackerMetrics.SampleGamesJob("running", null, "TEAM_BUILDER_RANKED_SOLO", List.of(
                new TrackerMetrics.SampleGamesRegion("EUW1", "analyzing", 50, 5, 45)
            )),
            new TrackerMetrics.GamesSummary(0, 0, 1755680420000L)
        );
    }

    private static WorkerMetrics sampleWorkers() {
        return new WorkerMetrics(List.of(
            new QueueWorkerStatus(1, "profile", "idle", null, null, null, 3, 3, List.of())
        ));
    }

    private static RiotMetrics sampleRiot() {
        return new RiotMetrics(
            12,
            List.of(
                new RiotMetrics.RiotQueue(
                    "EUW1",
                    "running",
                    "EUW1:match:abc-123",
                    1755680382000L,
                    new JobProgress(4, 12),
                    11,
                    12,
                    List.of("EUW1:summoner:def-456", "EUW1:match:ghi-789")
                )
            )
        );
    }

    @Test
    public void serializesCanonicalStatusShapeIncludingNullRedis() throws Exception {
        BotStatus status = BotStatus.online(
            new LeagueMetrics(4, 12, 18439201, 523891, 4123901, Map.of("RANKED_SOLO_5X5", 401203L, "RANKED_FLEX_SR", 122688L)),
            sampleTracker(),
            sampleWorkers(),
            sampleRiot(),
            new JvmMetrics(
                13.7,
                new JvmMetrics.Memory(1248231424L, 2147483648L, 4294967296L),
                87,
                120,
                8329401L
            ),
            new SystemMetrics(
                new SystemMetrics.Cpu(32.4, 6, List.of(22.3, 17.5, 45.1, 38.7, 31.2, 39.8)),
                new SystemMetrics.Memory(10485760000L, 6291456000L, 16777216000L),
                new SystemMetrics.Disk(53687091200L, 32212254720L, 85899345920L),
                new SystemMetrics.Network(1823912L, 728391L)
            ),
            null
        );

        String json = new ObjectMapper().writeValueAsString(status);

        assertTrue(json.contains("\"status\":\"online\""));
        assertTrue(json.contains("\"gameQueue\":4"));
        assertTrue(json.contains("\"profileQueue\":12"));
        assertTrue(json.contains("\"gamesAnalyzed\":18439201"));
        assertTrue(json.contains("\"totalSummoners\":523891"));
        assertTrue(json.contains("\"totalMasteries\":4123901"));
        assertTrue(json.contains("\"ranksByQueue\""));
        assertTrue(json.contains("RANKED_SOLO_5X5"));
        assertTrue(json.contains("401203"));
        assertTrue(json.contains("\"tracker\":{"));
        assertTrue(json.contains("\"workers\":{"));
        assertTrue(json.contains("\"riot\":{"));
        assertTrue(json.contains("\"totalInFlight\":12"));
        assertTrue(json.contains("\"inFlight\":3"));
        assertTrue(json.contains("\"queuedJobs\":["));
        assertTrue(json.contains("\"sampleGames\":{"));
        assertTrue(json.contains("\"summoners\":["));
        assertTrue(json.contains("\"done\":true"));
        assertTrue(json.contains("\"peakThreads\":120"));
        assertTrue(json.contains("\"receivedBytesPerSecond\":1823912"));
        assertTrue(json.contains("\"redis\":null"));
        assertTrue(json.contains("\"used\":1248231424"));
    }

    @Test
    public void keepsPartialSystemSectionsWhenRedisIsPresent() throws Exception {
        BotStatus status = BotStatus.online(
            new LeagueMetrics(0, 0, 0, 0, 0, Map.of()),
            null,
            null,
            null,
            null,
            new SystemMetrics(null, null, null, null),
            new RedisMetrics(163842L, 428392448L)
        );

        String json = new ObjectMapper().writeValueAsString(status);
        assertTrue(json.contains("\"keys\":163842"));
        assertTrue(json.contains("\"memoryUsed\":428392448"));
        assertNull(status.process());
    }
}
