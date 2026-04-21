package com.safjnest.lol;

import java.util.Arrays;
import java.util.List;

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
  
}
