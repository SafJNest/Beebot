package com.safjnest.lol.model.summoner;

import com.safjnest.lol.utils.GameQueueTypeUtils;

import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierDivisionType;

public record Rank(
    GameQueueType queue,
    TierDivisionType tier,
    int lp,
    int wins,
    int losses
) {
    public Rank {
        queue = GameQueueTypeUtils.canonicalQueue(
            queue == null ? GameQueueType.RANKED_SOLO_5X5 : queue
        );
        tier = tier == null ? TierDivisionType.UNRANKED : tier;
    }

    public static Rank unranked() {
        return new Rank(GameQueueType.RANKED_SOLO_5X5, TierDivisionType.UNRANKED, 0, 0, 0);
    }

    public int games() {
        return wins + losses;
    }

    public double winrate() {
        return games() == 0 ? 0 : (double) wins * 100 / games();
    }
}
