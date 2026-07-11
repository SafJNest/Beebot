package com.safjnest.spring.util;

import java.time.Duration;
import java.time.Instant;

import com.safjnest.lol.utils.ChampionUtils;
import no.stelar7.api.r4j.pojo.lol.staticdata.champion.StaticChampion;

public class LolApiMapper {

    public static String championName(int championId) {
        StaticChampion champion = ChampionUtils.getChampion(championId);
        return champion != null ? champion.getName() : String.valueOf(championId);
    }

    public static String championImage(int championId) {
        return ChampionUtils.getChampionProfilePic(championId);
    }

    public static String duration(long milliseconds) {
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        long remainingSeconds = seconds % 60;
        return minutes + ":" + String.format("%02d", remainingSeconds);
    }

    public static String ago(long timeStart) {
        long minutes = Duration.between(Instant.ofEpochMilli(timeStart), Instant.now()).toMinutes();
        if (minutes < 60) return minutes + "m";
        long hours = minutes / 60;
        if (hours < 24) return hours + "h";
        return (hours / 24) + "d";
    }

    public static double ratio(int part, int total) {
        return total > 0 ? (double) part / total : 0;
    }

    public static double rounded(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
