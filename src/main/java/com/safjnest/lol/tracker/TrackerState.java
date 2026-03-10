package com.safjnest.lol.tracker;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import com.safjnest.util.log.BotLogger;

public class TrackerState {

    public enum Priority { HIGH, MID, LOW }

    private static final Set<Priority> active = Collections.synchronizedSet(EnumSet.noneOf(Priority.class));

    public static void acquire(Priority p) {
        synchronized (TrackerState.class) {
            active.add(p);
        }
    }

    public static void release(Priority p) {
        synchronized (TrackerState.class) {
            active.remove(p);
            TrackerState.class.notifyAll();
        }
    }

    public static void awaitCondition(Priority p) {
        synchronized (TrackerState.class) {
            while (active.stream().anyMatch(r -> r.ordinal() < p.ordinal())) {
                BotLogger.warning("[TrackerState] " + Thread.currentThread().getName() + " waiting — active: " + active);
                try { TrackerState.class.wait(); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
            }
        }
    }
}