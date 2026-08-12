package com.safjnest.lol.model;

import java.util.List;

import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;

public record ChampionTierList(
    List<Role> roles,
    ResponseMetadata metadata
) {

    public ChampionTierList {
        roles = roles == null ? List.of() : List.copyOf(roles);
    }

    public ChampionTierList withMetadata(ResponseMetadata value) {
        return new ChampionTierList(roles, value);
    }

    public record Role(
        LaneType role,
        List<Champion> champions
    ) {
        public Role {
            champions = champions == null ? List.of() : List.copyOf(champions);
        }
    }

    public record Champion(
        ChampionView.Champion champion,
        boolean eligibleForRole,
        String tier,
        double tierScore,
        Statistics stats,
        List<Matchup> counters,
        List<Matchup> strongAgainst
    ) {
        public Champion {
            counters = counters == null ? List.of() : List.copyOf(counters);
            strongAgainst = strongAgainst == null ? List.of() : List.copyOf(strongAgainst);
        }
    }

    public record Statistics(
        int games,
        int picks,
        int bans,
        int wins,
        double winrate,
        double pickrate,
        Double banrate
    ) {}

    public record Matchup(
        ChampionView.Champion champion,
        int games,
        int wins,
        int losses,
        double winrate,
        double adjustedWinrate,
        double weightedDelta
    ) {}
}
