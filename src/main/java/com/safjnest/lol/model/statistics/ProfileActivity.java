package com.safjnest.lol.model.statistics;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.safjnest.lol.model.Filter;
import com.safjnest.lol.model.ResponseMetadata;
import com.safjnest.lol.model.match.Match;
import com.safjnest.lol.model.match.Participant;

import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;

public record ProfileActivity(
    Filter filter,
    Coverage coverage,
    Summary summary,
    Heatmap heatmap,
    List<TimeWindow> bestTimeWindows,
    List<DayActivity> dailyActivity,
    List<HourActivity> hourlyTrend,
    List<QueueActivity> queueActivity,
    List<Session> recentSessions,
    List<Insight> insights,
    ResponseMetadata metadata
) {

    public ProfileActivity(
        Filter filter,
        Coverage coverage,
        Summary summary,
        Heatmap heatmap,
        List<TimeWindow> bestTimeWindows,
        List<DayActivity> dailyActivity,
        List<HourActivity> hourlyTrend,
        List<QueueActivity> queueActivity,
        List<Session> recentSessions,
        List<Insight> insights
    ) {
        this(filter, coverage, summary, heatmap, bestTimeWindows, dailyActivity, hourlyTrend,
            queueActivity, recentSessions, insights, null);
    }

    private static final long DAY_MILLIS = 86_400_000L;
    private static final long SESSION_GAP_MILLIS = 90 * 60 * 1000L;
    private static final int MIN_BEST_WINDOW_GAMES = 3;
    private static final int MAX_BEST_TIME_WINDOWS = 3;
    private static final ZoneId ACTIVITY_ZONE = ZoneId.systemDefault();
    private static final List<String> DAYS = List.of(
        "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"
    );
    private static final List<Integer> HOURS = hours();

    public static ProfileActivity from(List<Match> matches, String puuid, Filter filter) {
        Accumulator accumulator = accumulator(puuid, filter);
        if (matches != null) for (Match match : matches) accumulator.accept(match);
        return accumulator.finish();
    }

    public static Accumulator accumulator(String puuid, Filter filter) {
        return new Accumulator(puuid, filter);
    }

    public static final class Accumulator {
        private final String puuid;
        private final Filter filter;
        private final Bucket[][] cells = buckets();
        private final Bucket[] days = new Bucket[DAYS.size()];
        private final Bucket[] hours = new Bucket[HOURS.size()];
        private final Map<GameQueueType, Bucket> queues = new HashMap<>();
        private final List<SessionAccumulator> sessions = new ArrayList<>();
        private long oldestMatchAt;
        private long newestMatchAt;
        private long previousMatchEnd;
        private SessionAccumulator currentSession;

        private Accumulator(String puuid, Filter filter) {
            this.puuid = puuid;
            this.filter = filter;
        }

        public void accept(Match match) {
            accept(match, participant(match, puuid));
        }

        public void accept(Match match, Participant player) {
            if (player == null) return;

            long timeStart = match.timeStart;
            long timeEnd = Math.max(timeStart, match.timeEnd);
            ZonedDateTime dateTime = Instant.ofEpochMilli(timeStart).atZone(ACTIVITY_ZONE);
            int day = dateTime.getDayOfWeek().getValue() - 1;
            int hour = dateTime.getHour();
            boolean win = player.win;

            cells[day][hour].add(win);
            days[day] = add(days[day], win);
            hours[hour] = add(hours[hour], win);
            if (match.queue != null) queues.computeIfAbsent(match.queue, ignored -> new Bucket()).add(win);

            if (currentSession == null || timeStart - previousMatchEnd > SESSION_GAP_MILLIS) {
                currentSession = new SessionAccumulator(timeStart);
                sessions.add(currentSession);
            }
            currentSession.add(timeEnd, win, match.queue, player.champion);
            previousMatchEnd = Math.max(previousMatchEnd, timeEnd);
            oldestMatchAt = oldestMatchAt == 0 ? timeStart : Math.min(oldestMatchAt, timeStart);
            newestMatchAt = Math.max(newestMatchAt, timeStart);
        }

        public ProfileActivity finish() {
            List<HeatmapCell> heatmapCells = new ArrayList<>(DAYS.size() * HOURS.size());
            List<DayActivity> dailyActivity = new ArrayList<>(DAYS.size());
            List<HourActivity> hourlyTrend = new ArrayList<>(HOURS.size());
            long totalGames = 0;
            long totalWins = 0;
            for (Bucket[] row : cells) for (Bucket cell : row) {
                totalGames += cell.games;
                totalWins += cell.wins;
            }
            for (int day = 0; day < DAYS.size(); day++) {
            Bucket dayBucket = value(days[day]);
            dailyActivity.add(new DayActivity(day, dayBucket.games, dayBucket.wins, dayBucket.losses(),
                share(dayBucket.games, totalGames), dayBucket.winrate()));
            for (int hour = 0; hour < HOURS.size(); hour++) {
                Bucket cell = cells[day][hour];
                heatmapCells.add(new HeatmapCell(day, hour, cell.games, cell.wins, cell.losses(), cell.winrate()));
            }
        }
        for (int hour = 0; hour < HOURS.size(); hour++) {
            Bucket hourBucket = value(hours[hour]);
            hourlyTrend.add(new HourActivity(hour, hourBucket.games, hourBucket.wins, hourBucket.losses(),
                share(hourBucket.games, totalGames), hourBucket.winrate()));
        }

        List<TimeWindow> bestTimeWindows = bestTimeWindows(cells);
        List<QueueActivity> queueActivity = queueActivity(queues, totalGames);
        List<Session> recentSessions = sessions(sessions);
        DayActivity mostActiveDay = mostActiveDay(dailyActivity);
        QueueActivity favoriteQueue = queueActivity.isEmpty() ? null : queueActivity.get(0);
        TimeWindow bestWinrateSlot = bestTimeWindows.isEmpty() ? null : bestTimeWindows.get(0);
        long rangeStart = filter != null && filter.timeStart() != 0 ? filter.timeStart() : oldestMatchAt;
        long rangeEnd = filter != null && filter.timeEnd() != 0 ? filter.timeEnd() : newestMatchAt;
        double rangeDays = rangeEnd > rangeStart ? (double) (rangeEnd - rangeStart) / DAY_MILLIS : 1;
        double gamesPerDay = round(totalGames / Math.max(1, rangeDays));
        long averageSessionDuration = averageSessionDuration(recentSessions);
        long sessionDurationStdDev = sessionDurationStdDev(recentSessions, averageSessionDuration);
        Summary summary = new Summary(
            totalGames,
            totalWins,
            totalGames - totalWins,
            winrate(totalWins, totalGames),
            gamesPerDay,
            mostActiveDay,
            bestWinrateSlot,
            favoriteQueue,
            recentSessions.size(),
            averageSessionDuration,
            sessionDurationStdDev
        );

            return new ProfileActivity(
            filter,
            new Coverage(totalGames, oldestMatchAt, newestMatchAt, System.currentTimeMillis()),
            summary,
            new Heatmap(DAYS, HOURS, List.copyOf(heatmapCells)),
            List.copyOf(bestTimeWindows),
            List.copyOf(dailyActivity),
            List.copyOf(hourlyTrend),
            List.copyOf(queueActivity),
            List.copyOf(recentSessions),
            insights(mostActiveDay, bestWinrateSlot, favoriteQueue),
            null
            );
        }
    }

    public ProfileActivity withMetadata(ResponseMetadata value) {
        return new ProfileActivity(filter, coverage, summary, heatmap, bestTimeWindows, dailyActivity,
            hourlyTrend, queueActivity, recentSessions, insights, value);
    }

    public record Coverage(
        long games,
        long oldestMatchAt,
        long newestMatchAt,
        long calculatedAt
    ) {}

    public record Summary(
        long games,
        long wins,
        long losses,
        Double winrate,
        double gamesPerDay,
        DayActivity mostActiveDay,
        TimeWindow bestWinrateSlot,
        QueueActivity favoriteQueue,
        long sessionCount,
        long averageSessionDurationMs,
        long sessionDurationStdDevMs
    ) {}

    public record Heatmap(
        List<String> days,
        List<Integer> hours,
        List<HeatmapCell> cells
    ) {}

    public record HeatmapCell(
        int day,
        int hour,
        long games,
        long wins,
        long losses,
        Double winrate
    ) {}

    public record TimeWindow(
        int rank,
        int day,
        int startHour,
        int endHour,
        long games,
        long wins,
        long losses,
        Double winrate
    ) {}

    public record DayActivity(
        int day,
        long games,
        long wins,
        long losses,
        double share,
        Double winrate
    ) {}

    public record HourActivity(
        int hour,
        long games,
        long wins,
        long losses,
        double share,
        Double winrate
    ) {}

    public record QueueActivity(
        GameQueueType queue,
        long games,
        long wins,
        long losses,
        double share,
        Double winrate
    ) {}

    public record Session(
        long start,
        long end,
        long durationMs,
        long games,
        long wins,
        long losses,
        Double winrate,
        List<GameQueueType> queues,
        List<Integer> championIds
    ) {}

    public record Insight(
        String code,
        Map<String, Object> values
    ) {}

    // ============================================================================

    private static List<TimeWindow> bestTimeWindows(Bucket[][] cells) {
        List<TimeWindow> candidates = new ArrayList<>();
        boolean hasMinimum = false;
        for (Bucket[] row : cells) for (Bucket cell : row) if (cell.games >= MIN_BEST_WINDOW_GAMES) hasMinimum = true;
        for (int day = 0; day < cells.length; day++) for (int hour = 0; hour < cells[day].length; hour++) {
            Bucket cell = cells[day][hour];
            if (cell.games == 0 || hasMinimum && cell.games < MIN_BEST_WINDOW_GAMES) continue;
            candidates.add(new TimeWindow(0, day, hour, hour + 1, cell.games, cell.wins,
                cell.losses(), cell.winrate()));
        }
        candidates.sort(Comparator
            .comparing(TimeWindow::winrate, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(TimeWindow::games, Comparator.reverseOrder())
            .thenComparingInt(TimeWindow::day)
            .thenComparingInt(TimeWindow::startHour));
        List<TimeWindow> result = new ArrayList<>(Math.min(MAX_BEST_TIME_WINDOWS, candidates.size()));
        for (int index = 0; index < candidates.size() && index < MAX_BEST_TIME_WINDOWS; index++) {
            TimeWindow value = candidates.get(index);
            result.add(new TimeWindow(index + 1, value.day(), value.startHour(), value.endHour(), value.games(),
                value.wins(), value.losses(), value.winrate()));
        }
        return result;
    }

    private static List<QueueActivity> queueActivity(Map<GameQueueType, Bucket> queues, long totalGames) {
        List<QueueActivity> result = new ArrayList<>();
        for (Map.Entry<GameQueueType, Bucket> entry : queues.entrySet()) {
            Bucket bucket = entry.getValue();
            result.add(new QueueActivity(entry.getKey(), bucket.games, bucket.wins, bucket.losses(),
                share(bucket.games, totalGames), bucket.winrate()));
        }
        result.sort(Comparator.comparing(QueueActivity::games, Comparator.reverseOrder())
            .thenComparing(value -> value.queue().name()));
        return result;
    }

    private static List<Session> sessions(List<SessionAccumulator> values) {
        List<Session> result = new ArrayList<>(values.size());
        for (int index = values.size() - 1; index >= 0; index--) result.add(values.get(index).toSession());
        return result;
    }

    private static DayActivity mostActiveDay(List<DayActivity> values) {
        DayActivity result = null;
        for (DayActivity value : values) {
            if (value.games() == 0) continue;
            if (result == null || value.games() > result.games()) result = value;
        }
        return result;
    }

    private static List<Insight> insights(
        DayActivity mostActiveDay,
        TimeWindow bestWinrateSlot,
        QueueActivity favoriteQueue
    ) {
        List<Insight> result = new ArrayList<>();
        if (mostActiveDay != null && mostActiveDay.games() > 0)
            result.add(new Insight("MOST_ACTIVE_DAY", Map.of(
                "day", dayName(mostActiveDay.day()),
                "games", mostActiveDay.games(),
                "share", mostActiveDay.share()
            )));
        if (bestWinrateSlot != null)
            result.add(new Insight("BEST_TIME_SLOT", Map.of(
                "day", dayName(bestWinrateSlot.day()),
                "hour", bestWinrateSlot.startHour(),
                "games", bestWinrateSlot.games(),
                "winrate", bestWinrateSlot.winrate()
            )));
        if (favoriteQueue != null)
            result.add(new Insight("FAVORITE_QUEUE", Map.of(
                "queue", favoriteQueue.queue(),
                "games", favoriteQueue.games(),
                "share", favoriteQueue.share()
            )));
        return List.copyOf(result);
    }

    private static long averageSessionDuration(List<Session> sessions) {
        if (sessions.isEmpty()) return 0;
        long total = 0;
        for (Session session : sessions) total += session.durationMs();
        return Math.round((double) total / sessions.size());
    }

    private static long sessionDurationStdDev(List<Session> sessions, long average) {
        if (sessions.isEmpty()) return 0;
        double sum = 0;
        for (Session session : sessions) {
            double difference = session.durationMs() - average;
            sum += difference * difference;
        }
        return Math.round(Math.sqrt(sum / sessions.size()));
    }

    private static Bucket[][] buckets() {
        Bucket[][] result = new Bucket[DAYS.size()][HOURS.size()];
        for (int day = 0; day < result.length; day++)
            for (int hour = 0; hour < result[day].length; hour++) result[day][hour] = new Bucket();
        return result;
    }

    private static Bucket add(Bucket value, boolean win) {
        Bucket result = value != null ? value : new Bucket();
        result.add(win);
        return result;
    }

    private static Bucket value(Bucket value) {
        return value != null ? value : new Bucket();
    }

    private static Participant participant(Match match, String puuid) {
        if (match == null || puuid == null || match.participants == null) return null;
        for (Participant value : match.participants) if (value != null && puuid.equals(value.puuid)) return value;
        return null;
    }

    private static String dayName(int day) {
        return DAYS.get(day);
    }

    private static List<Integer> hours() {
        List<Integer> result = new ArrayList<>(24);
        for (int hour = 0; hour < 24; hour++) result.add(hour);
        return List.copyOf(result);
    }

    private static Double winrate(long wins, long games) {
        return games > 0 ? round((double) wins / games * 100) : null;
    }

    private static double share(long games, long totalGames) {
        return totalGames > 0 ? round((double) games / totalGames * 100) : 0;
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static final class Bucket {
        private long games;
        private long wins;

        private void add(boolean win) {
            games++;
            if (win) wins++;
        }

        private long losses() {
            return games - wins;
        }

        private Double winrate() {
            return ProfileActivity.winrate(wins, games);
        }
    }

    private static final class SessionAccumulator {
        private final long start;
        private long end;
        private long games;
        private long wins;
        private final Set<GameQueueType> queues = new LinkedHashSet<>();
        private final Set<Integer> championIds = new LinkedHashSet<>();

        private SessionAccumulator(long start) {
            this.start = start;
            this.end = start;
        }

        private void add(long matchEnd, boolean win, GameQueueType queue, int championId) {
            end = Math.max(end, matchEnd);
            games++;
            if (win) wins++;
            if (queue != null) queues.add(queue);
            if (championId != 0) championIds.add(championId);
        }

        private Session toSession() {
            return new Session(start, end, Math.max(0, end - start), games, wins, games - wins,
                ProfileActivity.winrate(wins, games), List.copyOf(queues), List.copyOf(championIds));
        }
    }
}
