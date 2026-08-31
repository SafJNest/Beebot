package com.safjnest.lol.model.record;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.safjnest.lol.model.match.Participant;
import com.safjnest.lol.model.match.RankProgress;
import com.safjnest.lol.utils.TierDivisionUtils;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.TeamType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierDivisionType;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProfileRecord {

    public String puuid;
    public String filterKey;
    public RecordMetric metric;
    public long value;
    public long score;
    public String matchId;
    public long occurredAt;
    public int championId;
    public LeagueShard region;
    public TierDivisionType rank;
    public Integer lp;
    public Integer mmr;
    public TeamType team;
    public String actorPuuid;
    public Boolean gameShared;
    public long lastUpdate;

    public static ProfileRecord from(
        String puuid,
        String filterKey,
        RecordMetric metric,
        long value,
        String matchId,
        long occurredAt,
        Participant participant,
        LeagueShard region,
        TeamType team,
        String actorPuuid
    ) {
        ProfileRecord record = new ProfileRecord();
        record.puuid = puuid;
        record.filterKey = filterKey;
        record.metric = metric;
        record.value = value;
        record.score = metric.order().score(value);
        record.matchId = matchId;
        record.occurredAt = occurredAt;
        record.championId = participant == null ? 0 : participant.champion;
        record.region = region;
        record.team = team;
        record.actorPuuid = actorPuuid;
        record.gameShared = metric.gameShared() ? Boolean.TRUE : null;
        applyRank(record, participant == null ? null : participant.rankProgress);
        return record;
    }

    private static void applyRank(ProfileRecord record, RankProgress progress) {
        if (progress == null || progress.rank == null || progress.lp == null) return;
        int value = TierDivisionUtils.getMmr(progress.rank, progress.lp);
        if (value < 0) return;
        record.rank = progress.rank;
        record.lp = progress.lp;
        record.mmr = value;
    }
}
