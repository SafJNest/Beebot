package com.safjnest.util.lol;

import java.util.List;

import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.pojo.lol.staticdata.champion.StaticChampion;

public class LeagueMessageParameter {
  private LeagueMessageType messageType;

  private long[] period;
  
  private GameQueueType queueType;
  private LaneType laneType;

  private StaticChampion champion;
  private boolean showChampion;

  private int offset;

  public LeagueMessageParameter(LeagueMessageType messageType) {
    this.messageType = messageType;

    this.period = LeagueHandler.getCurrentSplitRange();

    this.queueType = null;
    this.laneType = null;

    this.champion = null;
    this.showChampion = false;

    this.offset = 0;

  }

  public LeagueMessageParameter(LeagueMessageType messageType, long[] period, GameQueueType queueType, LaneType laneType, StaticChampion champion, boolean showChampion, int offset) {
    this.messageType = messageType;

    this.period = period;
    
    this.queueType = queueType;
    this.laneType = laneType;

    this.champion = champion;
    this.showChampion = showChampion;

    this.offset = offset;
  }

  public LeagueMessageParameter(String prefix, List<Button> buttons) {
    this.period = LeagueHandler.getCurrentSplitRange();
    String timeString = "current";

    int fallbackChampion = 0;

    for (Button b : buttons) {
      boolean isActive = b.getStyle() == ButtonStyle.SUCCESS;
      String buttonValue = b.getCustomId().split("-").length == 2 ? b.getCustomId().split("-")[1] : b.getCustomId().split("-")[2];

      if (b.getCustomId().startsWith(prefix + "-queue-") && isActive) 
          this.queueType = GameQueueType.valueOf(buttonValue);
      
      if (b.getCustomId().startsWith(prefix + "-type-") && isActive)
          this.messageType = LeagueMessageType.valueOf(buttonValue);

      if (b.getCustomId().startsWith(prefix + "-lane-") && isActive)
          this.laneType = LaneType.valueOf(buttonValue);

      if (b.getCustomId().startsWith(prefix + "-champion-")) {
          this.champion = LeagueHandler.getChampionById(Integer.parseInt(buttonValue));
          this.showChampion = isActive;
      }
      
      if (b.getCustomId().startsWith(prefix + "-season-") && isActive)
          timeString = buttonValue;

      if (b.getCustomId().startsWith(prefix + "-leftpage")) 
          this.offset = Integer.parseInt(buttonValue);

      if (b.getCustomId().startsWith(prefix + "-change")) {
        fallbackChampion = Integer.parseInt(buttonValue);
        System.out.println(fallbackChampion);
      }
    }

    if (this.champion == null && fallbackChampion > 0) 
      this.champion = LeagueHandler.getChampionById(fallbackChampion);
    
    
    switch (timeString) {
        case "all":
            this.period = new long[] {0, 0};
            break;
        case "current":
            this.period = LeagueHandler.getCurrentSplitRange();
            break;
        case "previous":
            this.period = LeagueHandler.getPreviousSplitRange();
            break;
    }
  }

  public LeagueMessageType getMessageType() {
    return messageType;
  }

  public void setMessageType(LeagueMessageType messageType) {
      this.messageType = messageType;
  }

  public long[] getPeriod() {
      return period;
  }

  public void setPeriod(long[] period) {
      this.period = period;
  }

  public GameQueueType getQueueType() {
      return queueType;
  }

  public void setQueueType(GameQueueType queueType) {
      this.queueType = queueType;
  }

  public LaneType getLaneType() {
      return laneType;
  }

  public void setLaneType(LaneType laneType) {
      this.laneType = laneType;
  }

  public StaticChampion getChampion() {
      return champion;
  }

  public void setChampion(StaticChampion champion) {
      this.champion = champion;
  }

  public boolean isShowChampion() {
      return showChampion;
  }

  public void setShowChampion(boolean showChampion) {
      this.showChampion = showChampion;
  }

  public int getOffset() {
      return offset;
  }

  public void setOffset(int offset) {
      this.offset = offset;
  }

  public int getChampionId() {
    return champion != null ? champion.getId() : 0;
  }

  public boolean isDuo() {
    return laneType == LaneType.BOT || laneType == LaneType.UTILITY || queueType == GameQueueType.CHERRY;
  }

  public long getTimeStart() {
    return this.period[0];
  }

  public long getTimeEnd() {
    return this.period[1];
  }
}
