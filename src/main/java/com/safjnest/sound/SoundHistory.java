package com.safjnest.sound;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import com.safjnest.database.sql.database.BotDB.SoundPlay;

final class SoundHistory {

    static final int PAGE_SIZE = 100;
    static final int TIME_BUCKET_SECONDS = 15 * 60;

    private static final int HISTORY_CAPACITY = PAGE_SIZE + 1;
    private static final Comparator<SoundPlay> CHRONOLOGICAL = Comparator.comparing(SoundPlay::time)
        .thenComparingLong(play -> Long.parseLong(play.id()));

    private final SoundPeriod period;
    private final String viewerId;
    private final boolean detailed;

    private final Map<String, SoundData> sounds = new HashMap<>();
    private final Map<String, Long> previousPlays = new HashMap<>();
    private Group totals;

    SoundHistory(SoundPeriod period, String viewerId, boolean detailed) {
        this.period = period;
        this.viewerId = viewerId;
        this.detailed = detailed;
    }

    void recordPlay(SoundPlay play) {
        if (isPreviousPeriod(play)) {
            previousPlays.merge(play.soundId(), 1L, Long::sum);

            return;
        }

        SoundData sound = sounds.computeIfAbsent(play.soundId(), id -> new SoundData());
        sound.recordPlay(play, detailed);
        totals = null;

        if (detailed && isPersonalHistoryEntry(play))
            sound.addPersonalPlay(play);
    }

    Group getTotals() {
        if (totals == null) {
            Group combined = new Group();

            for (SoundData sound : sounds.values())
                combined.merge(sound.getTotals());

            totals = combined;
        }

        return totals;
    }

    Group getSoundTotals(String soundId) {
        SoundData sound = sounds.get(soundId);

        return sound == null ? new Group() : sound.getTotals();
    }

    Group getGuildTotals(String soundId, String guildId) {
        SoundData sound = sounds.get(soundId);
        if (sound == null)
            return new Group();

        return sound.guilds.getOrDefault(guildId, new Group());
    }

    Group getOtherGuildTotals(String soundId, Set<String> memberships) {
        Group combined = new Group();
        SoundData sound = sounds.get(soundId);
        if (sound == null)
            return combined;

        for (Map.Entry<String, Group> entry : sound.guilds.entrySet()) {
            if (!memberships.contains(entry.getKey()))
                combined.merge(entry.getValue());
        }

        return combined;
    }

    Instant getFirstPlay() {
        if (period.from() != null)
            return period.from();

        Instant earliest = period.to();

        for (SoundData sound : sounds.values()) {
            if (sound.firstPlay != null && sound.firstPlay.isBefore(earliest))
                earliest = sound.firstPlay;
        }

        return earliest;
    }

    Instant getFirstPlay(String soundId) {
        if (period.from() != null)
            return period.from();

        SoundData sound = sounds.get(soundId);

        return sound == null || sound.firstPlay == null ? period.to() : sound.firstPlay;
    }

    long getPreviousPlays() {
        long total = 0;

        for (long count : previousPlays.values())
            total += count;

        return total;
    }

    long getPreviousPlays(String soundId) {
        return previousPlays.getOrDefault(soundId, 0L);
    }

    List<SoundPlay> getPersonalPlays(String soundId) {
        SoundData sound = sounds.get(soundId);
        if (sound == null || sound.personalPlays == null)
            return List.of();

        List<SoundPlay> ordered = new ArrayList<>(sound.personalPlays);
        ordered.sort(CHRONOLOGICAL.reversed());

        return ordered;
    }

    /* -------------------------------------------------------------------------- */

    private boolean isPreviousPeriod(SoundPlay play) {
        return period.from() != null && play.time().isBefore(period.from());
    }

    private boolean isPersonalHistoryEntry(SoundPlay play) {
        if (!viewerId.equals(play.userId()))
            return false;

        if (period.before() == null)
            return true;

        int timeOrder = play.time().compareTo(period.before());
        if (timeOrder < 0)
            return true;

        if (timeOrder > 0)
            return false;

        return Long.parseLong(play.id()) < Long.parseLong(period.beforeId());
    }

    private record UserKey(String soundId, String userId) {}

    record UserTotals(long plays, int soundCount) {}

    static final class Group {

        private final Map<UserKey, Long> playsBySoundAndUser = new HashMap<>();
        private final Map<Long, Long> timeBuckets = new HashMap<>();
        private final Map<Integer, Long> sources = new HashMap<>();

        private long playCount;
        private Map<String, UserTotals> userTotals;

        long getPlayCount() {
            return playCount;
        }

        Map<String, UserTotals> getUserTotals() {
            if (userTotals == null) {
                Map<String, UserTotals> combined = new HashMap<>();

                for (Map.Entry<UserKey, Long> entry : playsBySoundAndUser.entrySet()) {
                    String userId = entry.getKey().userId();
                    UserTotals current = combined.getOrDefault(userId, new UserTotals(0, 0));
                    combined.put(userId, new UserTotals(current.plays() + entry.getValue(), current.soundCount() + 1));
                }

                userTotals = Collections.unmodifiableMap(combined);
            }

            return userTotals;
        }

        Map<Long, Long> getTimeBuckets() {
            return Collections.unmodifiableMap(timeBuckets);
        }

        Map<Integer, Long> getSources() {
            return Collections.unmodifiableMap(sources);
        }

        /* -------------------------------------------------------------------------- */

        private void recordPlay(SoundPlay play, boolean detailed) {
            UserKey user = new UserKey(play.soundId(), play.userId());
            playsBySoundAndUser.merge(user, 1L, Long::sum);
            playCount++;
            userTotals = null;

            if (!detailed)
                return;

            long bucket = Math.floorDiv(play.time().getEpochSecond(), TIME_BUCKET_SECONDS);
            timeBuckets.merge(bucket, 1L, Long::sum);
            sources.merge(play.source(), 1L, Long::sum);
        }

        private void merge(Group group) {
            playCount += group.playCount;
            userTotals = null;

            for (Map.Entry<UserKey, Long> entry : group.playsBySoundAndUser.entrySet())
                playsBySoundAndUser.merge(entry.getKey(), entry.getValue(), Long::sum);

            for (Map.Entry<Long, Long> entry : group.timeBuckets.entrySet())
                timeBuckets.merge(entry.getKey(), entry.getValue(), Long::sum);

            for (Map.Entry<Integer, Long> entry : group.sources.entrySet())
                sources.merge(entry.getKey(), entry.getValue(), Long::sum);
        }
    }

    private static final class SoundData {

        private final Map<String, Group> guilds = new HashMap<>();

        private Instant firstPlay;
        private Group totals;
        private PriorityQueue<SoundPlay> personalPlays;

        private void recordPlay(SoundPlay play, boolean detailed) {
            Group guild = guilds.computeIfAbsent(play.guildId(), id -> new Group());
            guild.recordPlay(play, detailed);
            totals = null;

            if (detailed && (firstPlay == null || play.time().isBefore(firstPlay)))
                firstPlay = play.time();
        }

        private Group getTotals() {
            if (totals == null) {
                Group combined = new Group();

                for (Group guild : guilds.values())
                    combined.merge(guild);

                totals = combined;
            }

            return totals;
        }

        private void addPersonalPlay(SoundPlay play) {
            if (personalPlays == null)
                personalPlays = new PriorityQueue<>(HISTORY_CAPACITY, CHRONOLOGICAL);

            if (personalPlays.size() < HISTORY_CAPACITY) {
                personalPlays.add(play);

                return;
            }

            if (CHRONOLOGICAL.compare(play, personalPlays.peek()) <= 0)
                return;

            personalPlays.poll();
            personalPlays.add(play);
        }
    }
}
