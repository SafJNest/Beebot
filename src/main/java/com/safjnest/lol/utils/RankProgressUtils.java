package com.safjnest.lol.utils;

import com.safjnest.lol.model.match.RankProgress;
import com.safjnest.lol.model.summoner.Rank;

import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierDivisionType;

public final class RankProgressUtils {

    private RankProgressUtils() {}

    public static RankProgress snapshot(Rank rank) {
        return rank == null ? null : snapshot(rank.tier(), rank.lp());
    }

    public static RankProgress snapshot(TierDivisionType rank, int lp) {
        return rank == null ? null : new RankProgress(rank, lp, null, null, null);
    }

    public static boolean hasCurrentSnapshot(RankProgress progress) {
        return progress != null && progress.rank != null && progress.lp != null;
    }

    public static boolean hasPreviousSnapshot(RankProgress progress) {
        return progress != null && progress.previousRank != null && progress.previousLp != null;
    }

    public static boolean hasCompleteProgress(RankProgress progress) {
        return hasCurrentSnapshot(progress) && progress.gain != null && hasPreviousSnapshot(progress);
    }

    public static RankProgress withPrevious(GameQueueType queue, RankProgress current, RankProgress previous) {
        if (!hasCurrentSnapshot(current)) return current;
        RankProgress result = new RankProgress(current.rank, current.lp, current.gain, null, null);
        if (!hasCurrentSnapshot(previous)) return result;

        result.previousRank = previous.rank;
        result.previousLp = previous.lp;
        result.gain = calculateGain(queue, result, previous);
        return result;
    }

    public static int calculateGain(GameQueueType queue, RankProgress current, RankProgress previous) {
        if (!GameQueueTypeUtils.isRankedSolo(queue) || !hasCurrentSnapshot(current) || !hasCurrentSnapshot(previous)) return 0;
        if (current.rank == TierDivisionType.UNRANKED) return 0;
        if (previous.rank == TierDivisionType.UNRANKED) return current.lp;

        boolean promotionToMaster = previous.rank == TierDivisionType.DIAMOND_I && current.rank == TierDivisionType.MASTER_I;
        boolean masterPlus = current.rank == TierDivisionType.MASTER_I || current.rank == TierDivisionType.GRANDMASTER_I
            || current.rank == TierDivisionType.CHALLENGER_I;
        if ((promotionToMaster || !masterPlus) && current.rank != previous.rank) {
            int gain = 100 - Math.abs(current.lp - previous.lp);
            return current.rank.ordinal() < previous.rank.ordinal() ? gain : -gain;
        }
        return current.lp - previous.lp;
    }

    public static boolean isPlacement(RankProgress progress) {
        return hasCurrentSnapshot(progress) && hasPreviousSnapshot(progress)
            && (progress.rank == TierDivisionType.UNRANKED || progress.previousRank == TierDivisionType.UNRANKED);
    }

    public static boolean isPromotion(RankProgress progress) {
        return hasPreviousSnapshot(progress) && !isPlacement(progress) && progress.rank.ordinal() < progress.previousRank.ordinal();
    }

    public static boolean isDemotion(RankProgress progress) {
        return hasPreviousSnapshot(progress) && !isPlacement(progress) && progress.rank.ordinal() > progress.previousRank.ordinal();
    }
}
