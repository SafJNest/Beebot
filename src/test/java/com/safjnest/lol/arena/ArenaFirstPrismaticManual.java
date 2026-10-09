package com.safjnest.lol.arena;

import java.util.Map;

import com.safjnest.lol.arena.FirstPrismaticResult.FinalItem;
import com.safjnest.lol.arena.FirstPrismaticResult.PrismaticEvent;
import com.safjnest.lol.model.match.Match;
import com.safjnest.lol.model.match.Participant;
import com.safjnest.lol.service.StaticDataService;
import com.safjnest.nosql.MongoDB;

import no.stelar7.api.r4j.pojo.lol.staticdata.champion.StaticChampion;
import no.stelar7.api.r4j.pojo.lol.staticdata.item.Item;

public final class ArenaFirstPrismaticManual {

    private static final String MATCH_ID = "BR1_3286062718";
    private static final PrismaticItemClassifier CLASSIFIER = new PrismaticItemClassifier(Map.of());

    private ArenaFirstPrismaticManual() {}

    public static void main(String[] args) {
        try {
            MongoDB.initialize();
            Match match = MongoDB.findMatch(MATCH_ID);
            if (match == null) {
                System.out.println("Match non trovato in Mongo: " + MATCH_ID);
                return;
            }
            if (match.participants == null || match.participants.isEmpty()) {
                System.out.println("Il match non contiene participant: " + MATCH_ID);
                return;
            }

            System.out.println("Match: " + MATCH_ID + " | patch=" + match.patch + " | queue=" + match.queue);
            System.out.println("Fonte timeline: match_events MongoDB (Match.eventData)");
            for (Participant participant : match.participants) printParticipant(match, participant);
        } catch (Exception exception) {
            System.err.println("Lettura match persistito fallita: " + exception.getMessage());
            exception.printStackTrace();
        }
    }

    private static void printParticipant(Match match, Participant participant) {
        if (participant == null) return;
        FirstPrismaticResult result = FirstPrismaticResolver.resolve(match.patch, participant, match.eventData, CLASSIFIER);
        StaticChampion champion = StaticDataService.getChampion(participant.champion);

        System.out.println("--------------------------------------------------");
        System.out.println("Participant " + participant.id);
        System.out.println("Riot ID: " + riotId(participant));
        System.out.println("Champion: " + (champion == null ? participant.champion : champion.getName()));
        System.out.println("Final items:");
        for (FinalItem item : result.finalItems())
            System.out.println("  slot" + item.slot() + ": " + item.itemId() + " " + itemName(item.itemId())
                    + " [" + item.classification() + "]");
        System.out.println("Final prismatics:");
        if (result.finalPrismatics().isEmpty()) System.out.println("  none");
        else for (int itemId : result.finalPrismatics()) System.out.println("  " + itemId + " " + itemName(itemId));
        System.out.println("220007 uses:");
        System.out.println("  count: " + result.anvilTimestamps().size());
        System.out.println("  timestamps: " + result.anvilTimestamps());
        System.out.println("Prismatic timeline events:");
        if (result.prismaticEvents().isEmpty()) System.out.println("  none");
        else for (PrismaticEvent event : result.prismaticEvents()) printEvent(event);
        System.out.println("RESOLVED FIRST PRISMATIC:");
        System.out.println("  " + (result.itemId() == null ? "NOT FOUND" : result.itemId() + " " + itemName(result.itemId())));
        System.out.println("Resolution:");
        System.out.println("  " + result.type());
        System.out.println("Reason:");
        System.out.println("  " + result.reason());
        System.out.println("Coverage:");
        System.out.println("  missing classifications: " + result.missingCount());
        System.out.println("  ambiguous evidence: " + result.ambiguousCount());
    }

    private static void printEvent(PrismaticEvent event) {
        System.out.println("  " + event.timestampMillis() + " " + event.eventType() + " "
                + event.itemId() + " " + itemName(event.itemId())
                + " before=" + event.beforeId() + " after=" + event.afterId()
                + (event.undoTargetType() == null ? "" : " undoTarget=" + event.undoTargetType())
                + (event.undone() ? " [UNDONE]" : ""));
    }

    private static String riotId(Participant participant) {
        if (participant.riotId == null || participant.riotId.isBlank()) return "unknown";
        if (participant.riotTag == null || participant.riotTag.isBlank()) return participant.riotId;
        return participant.riotId + "#" + participant.riotTag;
    }

    private static String itemName(int itemId) {
        Item item = StaticDataService.getItem(itemId);
        return item == null || item.getName() == null || item.getName().isBlank()
                ? "unknown (itemId=" + itemId + ")" : item.getName();
    }
}
