package com.safjnest.lol.service;

import static org.junit.Assert.*;
import static com.safjnest.lol.arena.ArenaGameParserTest.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

import com.safjnest.lol.model.ArenaBuildData;
import com.safjnest.lol.model.Filter;
import com.safjnest.lol.model.match.Match;
import com.safjnest.lol.model.status.JobProgress;
import com.safjnest.lol.queue.job.Job;
import com.safjnest.lol.queue.job.JobPriority;
import com.safjnest.lol.queue.scheduler.ComputeScheduler;
import com.safjnest.lol.queue.scheduler.DatabaseWorkerType;
import com.safjnest.lol.utils.MatchMemoryUtils;

import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;

public class ChampionServiceArenaTest {

    @Test
    public void streamsTimelineAndFallbackIntoOneDetachedPayloadAndReportsBoth() {
        AtomicReference<ArenaBuildData> saved = new AtomicReference<>();
        Job<Boolean> job = job();
        ChampionService service = new ChampionService((filter, consumer) -> {
            assertEquals(new JobProgress(0, 1), job.progress());
            Match timed = match("EUW1_1", tooltipPath(), List.of(event("ITEM_PURCHASED", 1000, 447001)));
            timed.restoreEvents();
            Match fallback = match("EUW1_2", tooltipPath(), List.of());
            fallback.eventData = null;
            List<Match> batch = new ArrayList<>(List.of(timed, fallback));
            consumer.accept(batch);
            MatchMemoryUtils.release(batch);
        }, (filter, payload) -> {
            assertEquals("WRITE total=2 completed=2 missing=1 failed=0", job.phase());
            assertEquals(new JobProgress(0, 1), job.progress());
            saved.set(payload);
            return true;
        });

        assertTrue(service.refreshArena(filter(), CATALOG, job));
        assertEquals(2, saved.get().stats().games());
        assertEquals(2, saved.get().coverage().matches());
        assertEquals(2, saved.get().coverage().missingCoreGames());
        assertEquals(1, saved.get().coverage().firstPrismaticFallbackGames());
        assertEquals("DONE total=2 completed=2 missing=1 failed=0", job.phase());
        assertEquals("champion=27 patch=16.19.1 shard=ALL", job.currentItem());
        assertEquals(new JobProgress(1, 1), job.progress());
        assertEquals(java.util.Map.of(job.key(), "DONE"), job.items());
    }

    @Test
    public void emptySourcePersistsAReadyEmptyArenaPayload() {
        AtomicReference<ArenaBuildData> saved = new AtomicReference<>();
        ChampionService service = new ChampionService((filter, consumer) -> {}, (filter, payload) -> {
            saved.set(payload);
            return true;
        });
        Job<Boolean> job = job();
        assertTrue(service.refreshArena(filter(), CATALOG, job));
        assertEquals(0, saved.get().stats().games());
        assertEquals("DONE total=0 completed=0 missing=0 failed=0", job.phase());
        assertEquals(new JobProgress(1, 1), job.progress());
    }

    @Test
    public void sourceFailurePreservesThePreviousPayloadAndFailsTheJob() {
        AtomicInteger writes = new AtomicInteger();
        IllegalStateException failure = new IllegalStateException("decode failed");
        ChampionService service = new ChampionService((filter, consumer) -> { throw failure; }, (filter, payload) -> {
            writes.incrementAndGet();
            return true;
        });
        Job<Boolean> job = job();
        assertSame(failure, assertThrows(IllegalStateException.class, () -> service.refreshArena(filter(), CATALOG, job)));
        assertEquals(0, writes.get());
        assertEquals("FAILED total=0 completed=0 missing=0 failed=1", job.phase());
        assertEquals(java.util.Map.of(job.key(), "FAILED"), job.items());
    }

    @Test
    public void invalidParticipantOrScopeFromSourceAbortsBeforeTheWriter() {
        for (boolean badScope : List.of(false, true)) {
            AtomicInteger writes = new AtomicInteger();
            Match invalid = match("EUW1_1", participant(1, 3), List.of());
            if (badScope) invalid.patch = "16.19.2";
            else invalid.participants.get(0).id = 0;
            ChampionService service = new ChampionService((filter, consumer) -> consumer.accept(List.of(invalid)),
                (filter, payload) -> { writes.incrementAndGet(); return true; });
            Job<Boolean> job = job();
            assertThrows(IllegalArgumentException.class, () -> service.refreshArena(filter(), CATALOG, job));
            assertEquals(0, writes.get());
            assertEquals("FAILED total=1 completed=0 missing=0 failed=1", job.phase());
        }
    }

    @Test
    public void writerRejectionAndExceptionBothFailTheJob() {
        for (boolean throwsFailure : List.of(false, true)) {
            ChampionService service = new ChampionService((filter, consumer) -> {}, (filter, payload) -> {
                if (throwsFailure) throw new IllegalStateException("BSON limit");
                return false;
            });
            Job<Boolean> job = job();
            assertThrows(IllegalStateException.class, () -> service.refreshArena(filter(), CATALOG, job));
            assertEquals("FAILED total=0 completed=0 missing=0 failed=1", job.phase());
            assertEquals(java.util.Map.of(job.key(), "FAILED"), job.items());
        }
    }

    @Test
    public void duplicateMatchFailsInsteadOfPersistingAPartialAggregate() {
        AtomicInteger writes = new AtomicInteger();
        Match match = match("EUW1_1", participant(1, 3), List.of());
        ChampionService service = new ChampionService((filter, consumer) -> consumer.accept(List.of(match, match)),
            (filter, payload) -> { writes.incrementAndGet(); return true; });
        Job<Boolean> job = job();
        assertThrows(IllegalArgumentException.class, () -> service.refreshArena(filter(), CATALOG, job));
        assertEquals(0, writes.get());
        assertEquals("FAILED total=2 completed=1 missing=1 failed=1", job.phase());
    }

    @Test
    public void jobItemMemoryIsConstantAcrossManyBatches() {
        Job<Boolean> job = job();
        ChampionService service = new ChampionService((filter, consumer) -> {
            for (int index = 1; index <= 200; index++) {
                consumer.accept(List.of(match("EUW1_" + index, participant(1, 3), List.of())));
                assertEquals(1, job.items().size());
            }
        }, (filter, payload) -> payload.stats().games() == 200);
        assertTrue(service.refreshArena(filter(), CATALOG, job));
        assertEquals(1, job.items().size());
        assertTrue(job.itemLabels().isEmpty());
        assertEquals("DONE total=200 completed=200 missing=200 failed=0", job.phase());
    }

    @Test
    public void snapshotRetainsRequestedIdentityWhenCallerChangesItsFilter() {
        Filter request = filter();
        ChampionService service = new ChampionService((snapshot, consumer) -> {
            assertNotSame(request, snapshot);
            request.setChampion(99).setPatch("16.19.2");
        }, (snapshot, payload) -> {
            assertEquals(27, snapshot.champion());
            assertEquals("16.19.1", snapshot.patch());
            return true;
        });
        assertTrue(service.refreshArena(request, CATALOG, job()));
    }

    @Test
    public void majorPatchExactRankBehaviorAndMissingCatalogFailBeforeSourceAccess() {
        AtomicInteger reads = new AtomicInteger();
        ChampionService service = new ChampionService((filter, consumer) -> reads.incrementAndGet(),
            (filter, payload) -> { fail("Writer must not be reached"); return false; });
        assertThrows(IllegalArgumentException.class,
            () -> service.refreshArena(filter().setPatch("16.19"), CATALOG, job()));
        assertThrows(IllegalArgumentException.class,
            () -> service.refreshArena(filter().setRankBehavior(Filter.RankBehavior.EXACT), CATALOG, job()));
        assertThrows(NullPointerException.class, () -> service.refreshArena(filter(), null, job()));
        assertEquals(0, reads.get());
    }

    private static Filter filter() {
        return Filter.championBuild(27, "16.19.1", GameQueueType.CHERRY);
    }

    private static Job<Boolean> job() {
        return new Job<>(1, 0, ComputeScheduler.class, DatabaseWorkerType.CHAMPION,
            "champion-arena:" + filter().toKey(), "arena test", JobPriority.BACKGROUND, ignored -> true);
    }
}
