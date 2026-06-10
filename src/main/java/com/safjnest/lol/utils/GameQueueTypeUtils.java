package com.safjnest.lol.utils;

import java.util.Arrays;
import java.util.List;

import com.safjnest.model.customemoji.CustomEmojiHandler;
import com.safjnest.utils.SafJNest;

import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;

public class GameQueueTypeUtils {

  private static final List<GameQueueType> QUEUES_WITHOUT_LANE = Arrays.asList(
    GameQueueType.CHERRY,
    GameQueueType.ULTBOOK,
    GameQueueType.URF,
    GameQueueType.ALL_RANDOM_URF,
    GameQueueType.DOOMBOTS_V2,
    GameQueueType.ONEFORALL_5X5,
    GameQueueType.ARAM,
    GameQueueType.ARAM_CLASH,
    GameQueueType.NEXUS_BLITZ,
    GameQueueType.STRAWBERRY
  );

  public static boolean hasLane(GameQueueType queue) {
    return !QUEUES_WITHOUT_LANE.contains(queue);
  }

  public static boolean isCherry(GameQueueType queue) {
    return queue == GameQueueType.CHERRY;
  }

  public static String prettyName(GameQueueType queue) {
    String name = switch (queue) {
        case CHERRY          -> "Arena";
        case STRAWBERRY      -> "Swarm";
        case ULTBOOK         -> "Ultimate Spellbook";
        case SWIFTPLAY       -> "Swiftplay";
        case URF,
             ALL_RANDOM_URF  -> "URF";
        case DOOMBOTS_V2     -> "DoomBots";
        default              -> SafJNest.capitalize(queue.name().replaceAll("_", " "));
    };

    return switch (queue.commonName()) {
        case "5v5 Ranked Solo"        -> "Ranked Solo/Duo";
        case "5v5 Ranked Flex Queue"  -> "Ranked Flex";
        case "5v5 Draft Pick"         -> "Draft Pick";
        default                       -> name;
    };
  }

  public static String getMapEmoji(GameQueueType type) {
    return switch (type) {
        case CHERRY,
             STRAWBERRY,
             NEXUS_BLITZ
            -> CustomEmojiHandler.getFormattedEmoji("arena_mode");

        case TEAM_BUILDER_RANKED_SOLO,
             RANKED_FLEX_SR,
             TEAM_BUILDER_DRAFT_UNRANKED_5X5,
             QUICKPLAY_NORMAL,
             SWIFTPLAY,
             NORMAL_5V5_BLIND_PICK
            -> CustomEmojiHandler.getFormattedEmoji("rift_mode");

        case ULTBOOK,
             URF,
             ALL_RANDOM_URF,
             ONEFORALL_5X5,
             DOOMBOTS_V2
            -> CustomEmojiHandler.getFormattedEmoji("special_mode");

        case ARAM,
             ARAM_CLASH
            -> CustomEmojiHandler.getFormattedEmoji("bridge_mode");

        default -> "";
    };
  }
}
