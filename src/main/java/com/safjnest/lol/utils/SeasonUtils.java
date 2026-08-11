package com.safjnest.lol.utils;

import java.io.File;
import java.io.FileReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

public final class SeasonUtils {

    private static final String SEASON_FILE = "rsc" + File.separator + "testing" + File.separator + "lol_testing" + File.separator + "split.json";
    private static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";

    private SeasonUtils() {}

    public record SeasonRange(int season, long start, long end) {}

    public static long[] getCurrentSplitRange() {
        JSONObject file = readFile();
        if (file == null) return null;

        long now = System.currentTimeMillis();
        JSONArray seasons = (JSONArray) file.get("seasons");
        for (int seasonIndex = seasons.size() - 1; seasonIndex >= 0; seasonIndex--) {
            JSONObject season = (JSONObject) seasons.get(seasonIndex);
            JSONArray splits = (JSONArray) season.get("splits");
            for (Object value : splits) {
                JSONObject split = (JSONObject) value;
                long start = parseDate(split.get("start_date"));
                long end = parseDate(split.get("end_date"));
                if (now >= start && now <= end) return new long[] {start, end};
            }
        }
        return new long[] {0, 0};
    }

    public static SeasonRange getCurrentSeasonRange() {
        JSONObject file = readFile();
        if (file == null) return null;

        long now = System.currentTimeMillis();
        JSONArray seasons = (JSONArray) file.get("seasons");
        for (Object value : seasons) {
            JSONObject season = (JSONObject) value;
            JSONArray splits = (JSONArray) season.get("splits");
            long start = Long.MAX_VALUE;
            long end = Long.MIN_VALUE;
            boolean current = false;
            for (Object splitValue : splits) {
                JSONObject split = (JSONObject) splitValue;
                long splitStart = parseDate(split.get("start_date"));
                long splitEnd = parseDate(split.get("end_date"));
                start = Math.min(start, splitStart);
                end = Math.max(end, splitEnd);
                current |= now >= splitStart && now <= splitEnd;
            }
            if (current) return new SeasonRange(Integer.parseInt(season.get("season").toString()), start, end);
        }
        return null;
    }

    public static long[] getPreviousSplitRange() {
        JSONObject file = readFile();
        if (file == null) return null;

        long now = System.currentTimeMillis();
        List<long[]> splits = new ArrayList<>();
        JSONArray seasons = (JSONArray) file.get("seasons");
        for (Object value : seasons) {
            JSONObject season = (JSONObject) value;
            for (Object splitValue : (JSONArray) season.get("splits")) {
                JSONObject split = (JSONObject) splitValue;
                splits.add(new long[] {parseDate(split.get("start_date")), parseDate(split.get("end_date"))});
            }
        }

        long[] current = null;
        for (long[] split : splits) {
            if (now >= split[0] && now <= split[1]) {
                current = split;
                break;
            }
        }
        if (current == null) return null;

        for (int i = splits.size() - 1; i >= 0; i--) {
            if (splits.get(i)[1] < current[0]) return splits.get(i);
        }
        return null;
    }

    public static String getCurrentSplitFormatted() {
        JSONObject file = readFile();
        if (file == null) return "No current split found";

        long now = System.currentTimeMillis();
        JSONArray seasons = (JSONArray) file.get("seasons");
        for (int seasonIndex = seasons.size() - 1; seasonIndex >= 0; seasonIndex--) {
            JSONObject season = (JSONObject) seasons.get(seasonIndex);
            for (Object value : (JSONArray) season.get("splits")) {
                JSONObject split = (JSONObject) value;
                long start = parseDate(split.get("start_date"));
                long end = parseDate(split.get("end_date"));
                if (now >= start && now <= end && split.get("is_current") != null) {
                    return "Season " + season.get("season") + " split " + split.get("split");
                }
            }
        }
        return "No current split found";
    }

    public static boolean isCurrentSplit(long time) {
        long[] range = getCurrentSplitRange();
        return range != null && time >= range[0] && time <= range[1];
    }

    // ============================================================================

    private static JSONObject readFile() {
        try (FileReader reader = new FileReader(SEASON_FILE)) {
            return (JSONObject) new JSONParser().parse(reader);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static long parseDate(Object value) {
        try {
            return new SimpleDateFormat(DATE_FORMAT).parse(value.toString()).getTime();
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid season date: " + value, exception);
        }
    }
}
