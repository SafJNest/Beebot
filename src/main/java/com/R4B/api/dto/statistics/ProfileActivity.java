package com.R4B.api.dto.statistics;

import java.util.List;
import java.util.Map;

import com.R4B.api.dto.*;
import com.R4B.lol.GameQueueType;

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
}
