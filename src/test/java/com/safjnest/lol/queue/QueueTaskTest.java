package com.safjnest.lol.queue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;

public class QueueTaskTest {

    @Test
    public void shouldCompleteAndCleanSuccessfulTask() {
        AtomicBoolean cleaned = new AtomicBoolean();
        QueueTask<String, Integer> task =
            new QueueTask<>("key", "name", "route", "queue", QueuePriority.IMMEDIATE, () -> 7);

        assertEquals(null, task.execute(() -> cleaned.set(true)));
        assertTrue(cleaned.get());
        assertEquals(Integer.valueOf(7), task.future().join());
    }

    @Test
    public void shouldCompleteExceptionallyAndCleanFailedTask() {
        AtomicBoolean cleaned = new AtomicBoolean();
        QueueTask<String, Integer> task = new QueueTask<>(
            "key",
            "name",
            "route",
            "queue",
            QueuePriority.IMMEDIATE,
            () -> {
                throw new IllegalStateException("expected");
            }
        );

        Throwable failure = task.execute(() -> cleaned.set(true));

        assertTrue(cleaned.get());
        assertTrue(failure instanceof IllegalStateException);
        try {
            task.future().join();
        } catch (CompletionException exception) {
            assertTrue(exception.getCause() instanceof IllegalStateException);
        }
    }

    @Test
    public void shouldPromoteOnlyToHigherPriority() {
        QueueTask<String, Integer> task =
            new QueueTask<>("key", "name", "route", "queue", QueuePriority.BACKGROUND, () -> 7);

        assertTrue(task.promote(QueuePriority.IMMEDIATE));
        assertEquals(QueuePriority.IMMEDIATE, task.priority());
        assertFalse(task.promote(QueuePriority.BACKGROUND));
    }

    @Test
    public void shouldCancelAndCleanPendingTask() {
        AtomicBoolean cleaned = new AtomicBoolean();
        QueueTask<String, Integer> task =
            new QueueTask<>("key", "name", "route", "queue", QueuePriority.IMMEDIATE, () -> 7);

        task.cancel("shutdown", () -> cleaned.set(true));

        assertTrue(cleaned.get());
        assertTrue(task.future().isCompletedExceptionally());
        try {
            task.future().join();
            throw new AssertionError("expected CancellationException");
        } catch (CancellationException expected) {
        }
    }
}
