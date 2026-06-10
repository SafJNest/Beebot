package com.safjnest.mongo.codec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bson.Document;

import com.safjnest.lol.dto.MatchDTO;
import com.safjnest.lol.dto.ParticipantDTO;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.TeamType;

public final class MatchDocumentCodec {

    private MatchDocumentCodec() {}

    public static Document encode(MatchDTO match) {
        Document bans = new Document();
        for (Map.Entry<TeamType, Integer> entry : match.getBans().entrySet()) {
            bans.append(entry.getKey().name(), entry.getValue());
        }

        List<Document> participants = new ArrayList<>();
        for (ParticipantDTO participant : match.getParticipants()) {
            participants.add(ParticipantDocumentCodec.encode(participant));
        }

        return new Document("_id", match.getGameId())
            .append("gameId", match.getGameId())
            .append("legacyId", match.getLegacyId())
            .append("leagueShard", enumName(match.getLeagueShard()))
            .append("queue", enumName(match.getQueue()))
            .append("bans", bans)
            .append("events", match.getEvents())
            .append("timeStart", match.getTimeStart())
            .append("timeEnd", match.getTimeEnd())
            .append("patch", match.getPatch())
            .append("participants", participants)
            .append("updatedAt", match.getUpdatedAt());
    }

    public static MatchDTO decode(Document document) {
        if (document == null) return null;

        Map<TeamType, Integer> bans = new LinkedHashMap<>();
        Document bansDocument = document.get("bans", Document.class);
        if (bansDocument != null) {
            for (Map.Entry<String, Object> entry : bansDocument.entrySet()) {
                TeamType team = enumValue(TeamType.class, entry.getKey());
                if (team != null) {
                    Integer champion = entry.getValue() instanceof Number number
                        ? number.intValue()
                        : null;
                    bans.put(team, champion);
                }
            }
        }

        List<ParticipantDTO> participants = new ArrayList<>();
        List<Document> participantDocuments = document.getList("participants", Document.class);
        if (participantDocuments != null) {
            for (Document participant : participantDocuments) {
                participants.add(ParticipantDocumentCodec.decode(participant));
            }
        }

        String gameId = MongoDocumentValues.string(document, "gameId");
        if (gameId == null) gameId = MongoDocumentValues.string(document, "_id");

        return new MatchDTO(
            MongoDocumentValues.integer(document, "legacyId"),
            gameId,
            MongoDocumentValues.enumValue(document, "leagueShard", LeagueShard.class),
            MongoDocumentValues.enumValue(document, "queue", GameQueueType.class),
            bans,
            MongoDocumentValues.string(document, "events"),
            MongoDocumentValues.longValue(document, "timeStart"),
            MongoDocumentValues.longValue(document, "timeEnd"),
            MongoDocumentValues.string(document, "patch"),
            participants,
            MongoDocumentValues.longValue(document, "updatedAt")
        );
    }

    private static <E extends Enum<E>> E enumValue(Class<E> enumType, String value) {
        if (value == null) return null;

        try {
            return Enum.valueOf(enumType, value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static String enumName(Enum<?> value) {
        return value != null ? value.name() : null;
    }
}
