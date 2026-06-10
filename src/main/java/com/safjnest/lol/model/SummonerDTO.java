package com.safjnest.lol.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.pojo.lol.league.LeagueEntry;
import no.stelar7.api.r4j.pojo.lol.summoner.Summoner;
import no.stelar7.api.r4j.pojo.shared.RiotAccount;

public final class SummonerDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String puuid;
    private final String summonerId;
    private final String accountId;
    private final String gameName;
    private final String tagLine;
    private final int profileIconId;
    private final long summonerLevel;
    private final long revisionDate;
    private final LeagueShard region;
    private final String userId;
    private final boolean banned;
    private final boolean tracking;
    private final List<RankDTO> ranks;
    private final long updatedAt;

    public SummonerDTO(
        String puuid,
        String summonerId,
        String accountId,
        String gameName,
        String tagLine,
        int profileIconId,
        long summonerLevel,
        long revisionDate,
        LeagueShard region,
        String userId,
        boolean banned,
        boolean tracking,
        List<RankDTO> ranks,
        long updatedAt
    ) {
        this.puuid = Objects.requireNonNull(puuid);
        this.summonerId = summonerId;
        this.accountId = accountId;
        this.gameName = gameName;
        this.tagLine = tagLine;
        this.profileIconId = profileIconId;
        this.summonerLevel = summonerLevel;
        this.revisionDate = revisionDate;
        this.region = region;
        this.userId = userId;
        this.banned = banned;
        this.tracking = tracking;
        this.ranks = ranks == null ? List.of() : List.copyOf(ranks);
        this.updatedAt = updatedAt;
    }

    public static SummonerDTO from(
        Summoner summoner,
        RiotAccount account,
        List<LeagueEntry> entries
    ) {
        List<RankDTO> ranks = new ArrayList<>();
        if (entries != null) {
            for (LeagueEntry entry : entries) {
                RankDTO rank = RankDTO.from(entry);
                if (rank != null) ranks.add(rank);
            }
        }

        return new SummonerDTO(
            summoner.getPUUID(),
            summoner.getSummonerId(),
            summoner.getAccountId(),
            account != null ? account.getName() : null,
            account != null ? account.getTag() : null,
            summoner.getProfileIconId(),
            summoner.getSummonerLevel(),
            summoner.getRevisionDate(),
            summoner.getPlatform(),
            null,
            false,
            false,
            ranks,
            System.currentTimeMillis()
        );
    }

    public String getPuuid() {
        return puuid;
    }

    public String getSummonerId() {
        return summonerId;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getGameName() {
        return gameName;
    }

    public String getTagLine() {
        return tagLine;
    }

    public int getProfileIconId() {
        return profileIconId;
    }

    public long getSummonerLevel() {
        return summonerLevel;
    }

    public long getRevisionDate() {
        return revisionDate;
    }

    public LeagueShard getRegion() {
        return region;
    }

    public String getUserId() {
        return userId;
    }

    public boolean isBanned() {
        return banned;
    }

    public boolean isTracking() {
        return tracking;
    }

    public List<RankDTO> getRanks() {
        return ranks;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }
}
