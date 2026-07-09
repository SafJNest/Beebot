package com.safjnest.lol.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.safjnest.lol.model.ProfileChampionStats;
import com.safjnest.lol.model.ProfileMatch;
import com.safjnest.lol.model.ProfilePageData;
import com.safjnest.lol.model.SummonerProfile;
import com.safjnest.lol.model.SummonerRank;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;

public class ProfilePageService {

    private static final int TOP_CHAMPIONS_LIMIT = 6;
    private static final int RECENT_MATCHES_LIMIT = 5;
    private static final int ROLE_MATCHES_LIMIT = 20;

    public ProfilePageData getProfile(String puuid, LeagueShard shard) {
        SummonerProfile profile = LeagueService.getProfileBase(puuid, shard);
        if (profile == null) return null;

        CompletableFuture<SummonerRank> rankFuture;
        CompletableFuture<List<ProfileMatch>> recentFuture;
        CompletableFuture<List<ProfileChampionStats>> topChampionsFuture;

        if (profile.summonerId() != 0) {
            int summonerId = profile.summonerId();
            rankFuture = CompletableFuture.supplyAsync(() -> LeagueService.getProfileRank(summonerId));
            recentFuture = CompletableFuture.supplyAsync(() -> LeagueService.getProfileRecentMatches(summonerId, ROLE_MATCHES_LIMIT));
            topChampionsFuture = CompletableFuture.supplyAsync(() -> LeagueService.getProfileTopChampions(summonerId, TOP_CHAMPIONS_LIMIT));
        } else {
            rankFuture = CompletableFuture.supplyAsync(() -> LeagueService.getProfileRank(profile.puuid(), shard));
            recentFuture = CompletableFuture.completedFuture(List.of());
            topChampionsFuture = CompletableFuture.completedFuture(List.of());
        }

        List<ProfileMatch> recentSource = recentFuture.join();
        List<ProfileMatch> recentMatches = limit(recentSource, RECENT_MATCHES_LIMIT);
        List<ProfilePageData.RoleStat> roles = roles(recentSource);

        return new ProfilePageData(
            profile,
            rankFuture.join(),
            summary(recentMatches, roles),
            roles,
            topChampionsFuture.join(),
            recentMatches
        );
    }

    private List<ProfileMatch> limit(List<ProfileMatch> matches, int limit) {
        List<ProfileMatch> result = new ArrayList<>();
        for (int i = 0; i < matches.size() && i < limit; i++) {
            result.add(matches.get(i));
        }
        return result;
    }

    private ProfilePageData.Summary summary(List<ProfileMatch> matches, List<ProfilePageData.RoleStat> roles) {
        StringBuilder form = new StringBuilder();
        int damage = 0;
        double kda = 0;

        for (ProfileMatch match : matches) {
            form.append(match.win() ? "W" : "L");
            damage += match.damage();
            kda += kdaRatio(match.kda());
        }

        return new ProfilePageData.Summary(
            form.toString(),
            roles.isEmpty() ? "" : roles.get(0).role(),
            matches.isEmpty() ? 0 : rounded(kda / matches.size()),
            matches.isEmpty() ? 0 : damage / matches.size()
        );
    }

    private List<ProfilePageData.RoleStat> roles(List<ProfileMatch> matches) {
        Map<String, Integer> counts = new HashMap<>();
        for (ProfileMatch match : matches) {
            String lane = match.lane();
            if (lane == null || lane.isBlank()) continue;
            counts.merge(lane, 1, Integer::sum);
        }

        int total = 0;
        for (int count : counts.values()) {
            total += count;
        }
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(counts.entrySet());
        entries.sort(Map.Entry.<String, Integer>comparingByValue().reversed());

        List<ProfilePageData.RoleStat> roles = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : entries) {
            roles.add(new ProfilePageData.RoleStat(
                entry.getKey(),
                entry.getValue(),
                total > 0 ? rounded((double) entry.getValue() / total * 100) : 0
            ));
        }
        return roles;
    }

    private double kdaRatio(String kda) {
        if (kda == null || kda.isBlank()) return 0;
        String[] parts = kda.split("/");
        if (parts.length != 3) return 0;
        int kills = parseInt(parts[0]);
        int deaths = parseInt(parts[1]);
        int assists = parseInt(parts[2]);
        return deaths > 0 ? rounded((double) (kills + assists) / deaths) : kills + assists;
    }

    private int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return 0;
        }
    }

    private double rounded(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
