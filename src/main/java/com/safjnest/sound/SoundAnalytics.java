package com.safjnest.sound;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.safjnest.bot.audio.types.AudioType;
import com.safjnest.discord.auth.DiscordApi;
import com.safjnest.database.sql.QueryRecord;
import com.safjnest.database.sql.database.BotDB;
import com.safjnest.database.sql.database.BotDB.SoundPlay;
import com.safjnest.sound.SoundHistory.Group;
import com.safjnest.sound.SoundHistory.UserTotals;

public final class SoundAnalytics {

    private static final int TOP_SOUND_LIMIT = 5;
    private static final int DAYS_PER_WEEK = 7;
    private static final int HOURS_PER_DAY = 24;

    private static final List<Integer> REPLAY_MINIMUMS = List.of(1, 2, 6, 21, 51);
    private static final List<Integer> REPLAY_MAXIMUMS = Arrays.asList(1, 5, 20, 50, null);

    private final BotDB database;
    private final DiscordApi access;

    public SoundAnalytics(BotDB database, DiscordApi access) {
        this.database = database;
        this.access = access;
    }

    public record DailyPlays(String date, long plays) {}
    public record HeatmapCell(int weekday, int hour, long plays) {}

    public record SourcePlays(String source, long plays) {}
    public record ReplayBucket(int min, Integer max, long users) {}

    public record Listener(Map<String, Object> user, long plays, int soundCount) {}
    public record RankedSound(String soundId, long plays) {}
    public record LikedSound(String soundId, long likes) {}

    public record GuildOverview(List<RankedSound> topSounds, Listener topListener) {}

    public record HistoryPlay(String id, String playedAt, String guildId, String source) {}
    public record PersonalHistory(long plays, List<HistoryPlay> history, String nextCursor) {}

    public Map<String, Object> getSummary(List<QueryRecord> sounds, String userId, String guildId, SoundPeriod period) {
        return getSummary(sounds, userId, guildId, period, false);
    }

    public Map<String, Object> getSummary(List<QueryRecord> sounds, String userId, String guildId,
            SoundPeriod period, boolean overview) {
        SoundHistory history = loadHistory(sounds, guildId, userId, period, false);
        Map<String, Object> summary = buildSummary(sounds, userId, period, history);

        if (overview) {
            List<Listener> leaders = buildLeaderboard(history.getTotals(), 1);
            Listener topListener = leaders.isEmpty() ? null : leaders.getFirst();

            summary.put("overview", new GuildOverview(buildTopPlayedSounds(sounds, history), topListener));
        }

        return summary;
    }

    public Map<String, Object> getGuildAnalytics(List<QueryRecord> sounds, String userId, String guildId,
            SoundPeriod period, Set<String> memberships) {
        SoundHistory history = loadHistory(sounds, guildId, userId, period, true);
        Group totals = history.getTotals();
        Instant firstPlay = history.getFirstPlay();

        List<Map<String, Object>> details = new ArrayList<>();
        for (QueryRecord sound : sounds)
            details.add(buildGuildSoundAnalytics(sound, userId, guildId, period, memberships, history, firstPlay));

        Map<String, Object> summary = buildTotals(totals, userId, true);
        summary.put("likes", sumSoundValues(sounds, "likes"));
        summary.put("dislikes", sumSoundValues(sounds, "dislikes"));
        summary.put("soundCount", sounds.size());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("guildId", guildId);
        result.putAll(buildPeriodFields(period));
        result.put("summary", summary);
        result.put("playsPerDay", buildDailySeries(totals, period, firstPlay));
        result.put("topSounds", buildTopPlayedSounds(sounds, history));
        result.put("mostLiked", buildMostLikedSounds(sounds));
        result.put("users", buildLeaderboard(totals));
        result.put("sounds", details);

        return result;
    }

    public Map<String, Object> getSoundAnalytics(QueryRecord sound, String userId, String guildId,
            SoundPeriod period, Set<String> memberships) {
        SoundHistory history = loadHistory(List.of(sound), guildId, userId, period, true);

        return buildSoundAnalytics(sound, userId, guildId, period, memberships, history);
    }

    public Map<String, Object> getUserAnalytics(List<QueryRecord> sounds, String userId,
            SoundPeriod period, Set<String> memberships) {
        SoundHistory history = loadHistory(sounds, null, userId, period, true);
        List<Map<String, Object>> details = new ArrayList<>();

        for (QueryRecord sound : sounds)
            details.add(buildSoundAnalytics(sound, userId, null, period, memberships, history));

        Map<String, Object> result = buildPeriodFields(period);
        result.put("summary", buildSummary(sounds, userId, period, history));
        result.put("sounds", details);

        return result;
    }

    /* -------------------------------------------------------------------------- */

    private SoundHistory loadHistory(List<QueryRecord> sounds, String guildId, String userId,
            SoundPeriod period, boolean detailed) {
        List<String> ids = new ArrayList<>();
        for (QueryRecord sound : sounds)
            ids.add(sound.get("id"));

        SoundHistory history = new SoundHistory(period, userId, detailed);
        Instant previousStart = period.from() == null ? null : period.previous().from();
        database.forEachSoundPlay(ids, guildId, previousStart, period.to(), history::recordPlay);

        return history;
    }

    private Map<String, Object> buildGuildSoundAnalytics(QueryRecord sound, String userId, String guildId,
            SoundPeriod period, Set<String> memberships, SoundHistory history, Instant firstPlay) {
        String soundId = sound.get("id");
        Group totals = history.getSoundTotals(soundId);
        Map<String, Object> result = buildGroupAnalytics(totals, period, firstPlay, true);

        result.put("soundId", soundId);
        result.put("likes", sound.getAsLong("likes"));
        result.put("dislikes", sound.getAsLong("dislikes"));
        addPreviousPeriod(result, totals.getPlayCount(), history.getPreviousPlays(soundId), period);
        result.put("my", buildPersonalHistory(soundId, userId, guildId, period, memberships, history));

        return result;
    }

    private Map<String, Object> buildSoundAnalytics(QueryRecord sound, String userId, String guildId,
            SoundPeriod period, Set<String> memberships, SoundHistory history) {
        String soundId = sound.get("id");
        Instant firstPlay = history.getFirstPlay(soundId);
        Group soundTotals = history.getSoundTotals(soundId);
        Group otherGuilds = history.getOtherGuildTotals(soundId, memberships);

        Map<String, Object> guilds = new LinkedHashMap<>();
        for (String membership : memberships) {
            if (guildId != null && !guildId.equals(membership))
                continue;

            Group guild = history.getGuildTotals(soundId, membership);
            if (guild.getPlayCount() == 0)
                continue;

            guilds.put(membership, buildGroupAnalytics(guild, period, firstPlay, true));
        }

        Map<String, Object> totals = buildGroupAnalytics(soundTotals, period, firstPlay, false);
        totals.put("likes", sound.getAsLong("likes"));
        totals.put("dislikes", sound.getAsLong("dislikes"));
        addPreviousPeriod(totals, soundTotals.getPlayCount(), history.getPreviousPlays(soundId), period);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("soundId", soundId);
        result.putAll(buildPeriodFields(period));
        result.put("totals", totals);
        result.put("guilds", guilds);
        result.put("otherGuilds", buildGroupAnalytics(otherGuilds, period, firstPlay, false));
        result.put("my", buildPersonalHistory(soundId, userId, guildId, period, memberships, history));

        return result;
    }

    private Map<String, Object> buildSummary(List<QueryRecord> sounds, String userId, SoundPeriod period, SoundHistory history) {
        Group totals = history.getTotals();
        Map<String, Object> result = buildTotals(totals, userId, true);

        result.put("period", period.period());
        result.put("likes", sumSoundValues(sounds, "likes"));
        result.put("dislikes", sumSoundValues(sounds, "dislikes"));
        result.put("soundCount", sounds.size());
        addPreviousPeriod(result, totals.getPlayCount(), history.getPreviousPlays(), period);

        return result;
    }

    private Map<String, Object> buildGroupAnalytics(Group group, SoundPeriod period, Instant firstPlay, boolean includeUsers) {
        Map<String, Object> result = buildTotals(group, null, false);

        result.put("playsPerDay", buildDailySeries(group, period, firstPlay));
        result.put("heatmap", buildHeatmap(group, period));
        result.put("sources", buildSourceDistribution(group));
        result.put("replayDistribution", buildReplayDistribution(group));

        if (includeUsers)
            result.put("users", buildLeaderboard(group));

        return result;
    }

    private Map<String, Object> buildTotals(Group group, String userId, boolean includePersonal) {
        long plays = group.getPlayCount();
        Map<String, UserTotals> users = group.getUserTotals();
        int listeners = users.size();
        double average = listeners == 0 ? 0.0 : (double) group.getIdentifiedPlayCount() / listeners;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("plays", plays);
        result.put("listeners", listeners);
        result.put("averagePlaysPerListener", average);

        if (includePersonal) {
            UserTotals personal = users.get(userId);
            result.put("myPlays", personal == null ? 0L : personal.plays());
        }

        return result;
    }

    private PersonalHistory buildPersonalHistory(String soundId, String userId, String guildId, SoundPeriod period,
            Set<String> memberships, SoundHistory history) {
        UserTotals personal = history.getSoundTotals(soundId).getUserTotals().get(userId);
        long plays = personal == null ? 0L : personal.plays();
        List<SoundPlay> rows = history.getPersonalPlays(soundId);
        String nextCursor = null;

        if (rows.size() > SoundHistory.PAGE_SIZE) {
            SoundPlay last = rows.get(SoundHistory.PAGE_SIZE - 1);
            nextCursor = period.cursor(last.id(), last.time(), soundId, userId, guildId);
        }

        List<HistoryPlay> entries = new ArrayList<>();
        int pageSize = Math.min(SoundHistory.PAGE_SIZE, rows.size());

        for (SoundPlay play : rows.subList(0, pageSize)) {
            String visibleGuild = play.guildId() != null && memberships.contains(play.guildId()) ? play.guildId() : null;
            entries.add(new HistoryPlay(play.id(), play.time().toString(), visibleGuild, getSourceName(play.source())));
        }

        return new PersonalHistory(plays, entries, nextCursor);
    }

    private List<DailyPlays> buildDailySeries(Group group, SoundPeriod period, Instant firstPlay) {
        Map<LocalDate, Long> counts = new HashMap<>();

        for (Map.Entry<Long, Long> entry : group.getTimeBuckets().entrySet()) {
            LocalDate date = getBucketTime(entry.getKey(), period).toLocalDate();
            counts.merge(date, entry.getValue(), Long::sum);
        }

        LocalDate start = firstPlay.atZone(period.zone()).toLocalDate();
        LocalDate end = period.to().minusNanos(1).atZone(period.zone()).toLocalDate();
        List<DailyPlays> result = new ArrayList<>();

        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1))
            result.add(new DailyPlays(date.toString(), counts.getOrDefault(date, 0L)));

        return result;
    }

    private List<HeatmapCell> buildHeatmap(Group group, SoundPeriod period) {
        long[][] cells = new long[DAYS_PER_WEEK][HOURS_PER_DAY];

        for (Map.Entry<Long, Long> entry : group.getTimeBuckets().entrySet()) {
            ZonedDateTime time = getBucketTime(entry.getKey(), period);
            int weekday = time.getDayOfWeek().getValue() - 1;
            cells[weekday][time.getHour()] += entry.getValue();
        }

        List<HeatmapCell> result = new ArrayList<>();
        for (int day = 0; day < DAYS_PER_WEEK; day++) {
            for (int hour = 0; hour < HOURS_PER_DAY; hour++)
                result.add(new HeatmapCell(day + 1, hour, cells[day][hour]));
        }

        return result;
    }

    private List<SourcePlays> buildSourceDistribution(Group group) {
        Map<String, Long> counts = new TreeMap<>();
        for (Map.Entry<Integer, Long> entry : group.getSources().entrySet())
            counts.merge(getSourceName(entry.getKey()), entry.getValue(), Long::sum);

        List<SourcePlays> result = new ArrayList<>();
        for (Map.Entry<String, Long> entry : counts.entrySet())
            result.add(new SourcePlays(entry.getKey(), entry.getValue()));

        return result;
    }

    private List<ReplayBucket> buildReplayDistribution(Group group) {
        long[] counts = new long[REPLAY_MINIMUMS.size()];
        for (UserTotals user : group.getUserTotals().values())
            counts[getReplayBucket(user.plays())]++;

        List<ReplayBucket> result = new ArrayList<>();
        for (int index = 0; index < counts.length; index++)
            result.add(new ReplayBucket(REPLAY_MINIMUMS.get(index), REPLAY_MAXIMUMS.get(index), counts[index]));

        return result;
    }

    private List<Listener> buildLeaderboard(Group group) {
        return buildLeaderboard(group, Integer.MAX_VALUE);
    }

    private List<Listener> buildLeaderboard(Group group, int limit) {
        Map<String, UserTotals> counts = group.getUserTotals();
        List<String> ids = new ArrayList<>(counts.keySet());
        ids.sort(Comparator.<String>comparingLong(id -> counts.get(id).plays()).reversed()
            .thenComparing(Comparator.naturalOrder()));

        List<Listener> result = new ArrayList<>();
        for (String id : ids.subList(0, Math.min(ids.size(), limit))) {
            UserTotals user = counts.get(id);
            result.add(new Listener(access.user(id), user.plays(), user.soundCount()));
        }

        return result;
    }

    private List<RankedSound> buildTopPlayedSounds(List<QueryRecord> sounds, SoundHistory history) {
        List<RankedSound> ranked = new ArrayList<>();
        for (QueryRecord sound : sounds) {
            String id = sound.get("id");
            ranked.add(new RankedSound(id, history.getSoundTotals(id).getPlayCount()));
        }

        ranked.sort(Comparator.comparingLong(RankedSound::plays).reversed());

        return new ArrayList<>(ranked.subList(0, Math.min(TOP_SOUND_LIMIT, ranked.size())));
    }

    private List<LikedSound> buildMostLikedSounds(List<QueryRecord> sounds) {
        List<LikedSound> ranked = new ArrayList<>();
        for (QueryRecord sound : sounds)
            ranked.add(new LikedSound(sound.get("id"), sound.getAsLong("likes")));

        ranked.sort(Comparator.comparingLong(LikedSound::likes).reversed());

        return new ArrayList<>(ranked.subList(0, Math.min(TOP_SOUND_LIMIT, ranked.size())));
    }

    private Map<String, Object> buildPeriodFields(SoundPeriod period) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("period", period.period());
        result.put("timezone", period.zone().getId());
        result.put("from", period.from() == null ? null : period.from().toString());
        result.put("to", period.to().toString());

        return result;
    }

    private void addPreviousPeriod(Map<String, Object> result, long current, long previous, SoundPeriod period) {
        result.put("previousPlays", period.from() == null ? null : previous);
        result.put("playsChangePercent", period.from() == null || previous == 0 ? null : 100.0 * (current - previous) / previous);
    }

    private ZonedDateTime getBucketTime(long bucket, SoundPeriod period) {
        return Instant.ofEpochSecond(bucket * SoundHistory.TIME_BUCKET_SECONDS).atZone(period.zone());
    }

    private int getReplayBucket(long plays) {
        if (plays == 1)
            return 0;

        if (plays <= 5)
            return 1;

        if (plays <= 20)
            return 2;

        if (plays <= 50)
            return 3;

        return 4;
    }

    private long sumSoundValues(List<QueryRecord> sounds, String column) {
        long total = 0;

        for (QueryRecord sound : sounds)
            total += sound.getAsLong(column);

        return total;
    }

    private String getSourceName(int ordinal) {
        AudioType source = AudioType.getFromOrdinal(ordinal);

        return source == null ? "UNKNOWN" : source.name();
    }
}
