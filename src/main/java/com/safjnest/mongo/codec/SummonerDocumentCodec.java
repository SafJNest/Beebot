package com.safjnest.mongo.codec;

import java.util.ArrayList;
import java.util.List;

import org.bson.Document;

import com.safjnest.lol.model.RankDTO;
import com.safjnest.lol.model.SummonerDTO;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierDivisionType;

public final class SummonerDocumentCodec {

    private SummonerDocumentCodec() {}

    public static Document encode(SummonerDTO summoner) {
        List<Document> ranks = new ArrayList<>();
        for (RankDTO rank : summoner.getRanks()) ranks.add(encode(rank));

        return new Document("_id", id(summoner.getPuuid(), summoner.getRegion()))
            .append("puuid", summoner.getPuuid())
            .append("summonerId", summoner.getSummonerId())
            .append("accountId", summoner.getAccountId())
            .append("gameName", summoner.getGameName())
            .append("tagLine", summoner.getTagLine())
            .append("profileIconId", summoner.getProfileIconId())
            .append("summonerLevel", summoner.getSummonerLevel())
            .append("revisionDate", summoner.getRevisionDate())
            .append("region", name(summoner.getRegion()))
            .append("userId", summoner.getUserId())
            .append("banned", summoner.isBanned())
            .append("tracking", summoner.isTracking())
            .append("ranks", ranks)
            .append("updatedAt", summoner.getUpdatedAt());
    }

    public static SummonerDTO decode(Document document) {
        if (document == null) return null;

        List<RankDTO> ranks = new ArrayList<>();
        List<Document> rankDocuments = document.getList("ranks", Document.class);
        if (rankDocuments != null) {
            for (Document rank : rankDocuments) ranks.add(decodeRank(rank));
        }

        return new SummonerDTO(
            document.getString("puuid"),
            document.getString("summonerId"),
            document.getString("accountId"),
            document.getString("gameName"),
            document.getString("tagLine"),
            integer(document, "profileIconId"),
            longValue(document, "summonerLevel"),
            longValue(document, "revisionDate"),
            enumValue(LeagueShard.class, document.getString("region")),
            document.getString("userId"),
            bool(document, "banned"),
            bool(document, "tracking"),
            ranks,
            longValue(document, "updatedAt")
        );
    }

    public static String id(String puuid, LeagueShard region) {
        return region.name() + ":" + puuid;
    }

    private static Document encode(RankDTO rank) {
        return new Document("queue", name(rank.getQueue()))
            .append("rank", name(rank.getRank()))
            .append("leagueId", rank.getLeagueId())
            .append("leaguePoints", rank.getLeaguePoints())
            .append("wins", rank.getWins())
            .append("losses", rank.getLosses());
    }

    private static RankDTO decodeRank(Document document) {
        return new RankDTO(
            enumValue(GameQueueType.class, document.getString("queue")),
            enumValue(TierDivisionType.class, document.getString("rank")),
            document.getString("leagueId"),
            integer(document, "leaguePoints"),
            integer(document, "wins"),
            integer(document, "losses")
        );
    }

    private static int integer(Document document, String key) {
        Number value = document.get(key, Number.class);
        return value != null ? value.intValue() : 0;
    }

    private static long longValue(Document document, String key) {
        Number value = document.get(key, Number.class);
        return value != null ? value.longValue() : 0;
    }

    private static boolean bool(Document document, String key) {
        Boolean value = document.getBoolean(key);
        return value != null && value;
    }

    private static String name(Enum<?> value) {
        return value != null ? value.name() : null;
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String value) {
        if (value == null) return null;
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
