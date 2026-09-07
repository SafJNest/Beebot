package com.safjnest.lol.utils;

public final class KdaUtils {

    private static final int KILLS_INDEX = 0;
    private static final int DEATHS_INDEX = 1;
    private static final int ASSISTS_INDEX = 2;

    private KdaUtils() {}

    public static int[] parse(String kda) {
        if (kda == null || kda.isBlank()) return new int[3];
        String[] parts = kda.split("/");
        if (parts.length != 3) return new int[3];
        return new int[]{
            NumberUtils.parseInt(parts[KILLS_INDEX]),
            NumberUtils.parseInt(parts[DEATHS_INDEX]),
            NumberUtils.parseInt(parts[ASSISTS_INDEX])
        };
    }

    public static boolean isValid(String kda) {
        if (kda == null || kda.isBlank()) return false;
        return kda.split("/").length == 3;
    }

    public static int getKills(String kda) {
        return parse(kda)[KILLS_INDEX];
    }

    public static int getDeaths(String kda) {
        return parse(kda)[DEATHS_INDEX];
    }

    public static int getAssists(String kda) {
        return parse(kda)[ASSISTS_INDEX];
    }

    public static int value(String kda, int index) {
        if (kda == null || kda.isBlank()) return 0;
        if (index < KILLS_INDEX || index > ASSISTS_INDEX) return 0;
        String[] parts = kda.split("/");
        if (parts.length != 3) return 0;
        return NumberUtils.parseInt(parts[index]);
    }
}
