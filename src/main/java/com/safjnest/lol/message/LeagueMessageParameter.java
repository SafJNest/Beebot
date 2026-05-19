package com.safjnest.lol.message;

import java.util.List;

import com.safjnest.lol.LeagueHandler;
import com.safjnest.lol.model.Filter;
import com.safjnest.lol.utils.ChampionUtils;
import com.safjnest.lol.utils.GameQueueTypeUtils;

import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierType;
import no.stelar7.api.r4j.pojo.lol.match.v5.LOLMatch;
import no.stelar7.api.r4j.pojo.lol.staticdata.champion.StaticChampion;

public class LeagueMessageParameter {
  private LeagueMessageType messageType;

  private long[] period;
  
  private GameQueueType queueType;
  private LaneType laneType;

  private StaticChampion champion;
  private boolean showChampion;

  private int offset;

  private LOLMatch match;

  private StringSelectMenu livegameMenu;
  private StringSelectMenu opggMenu;

  private Filter filter;

  public LeagueMessageParameter(LeagueMessageType messageType) {
    this.messageType = messageType;

    this.period = LeagueHandler.getCurrentSplitRange();

    this.queueType = null;
    this.laneType = null;

    this.champion = null;
    this.showChampion = false;

    this.offset = 0;

    this.filter = new Filter();
  }

  public LeagueMessageParameter(LeagueMessageType messageType, Filter filter) {
    this.messageType = messageType;
    this.filter = filter;
  }

  public LeagueMessageParameter(LeagueMessageType messageType, long[] period, GameQueueType queueType, LaneType laneType, StaticChampion champion, boolean showChampion, int offset) {
    this.messageType = messageType;

    this.period = period;
    
    this.queueType = queueType;
    this.laneType = laneType;

    this.champion = champion;
    this.showChampion = showChampion;

    this.offset = offset;

    this.match = null;
  }

  public LeagueMessageParameter(List<Button> buttons) {
    String prefix = LeagueMessage.BUTTON_ID_PREFIX;
    
    this.period = LeagueHandler.getCurrentSplitRange();
    String timeString = "current";

    int fallbackChampion = 0;

    for (Button b : buttons) {
      boolean isActive = b.getStyle() == ButtonStyle.SUCCESS;
      String buttonValue = b.getCustomId().split("-").length == 2 ? b.getCustomId().split("-")[1] : b.getCustomId().split("-")[2];
      if (b.getCustomId().startsWith(prefix + "-queue-") && isActive) {
        try {
          this.queueType = GameQueueType.valueOf(buttonValue);
        } catch (Exception e) { }
      }
      
      if (b.getCustomId().startsWith(prefix + "-type-") && isActive)
          this.messageType = LeagueMessageType.valueOf(buttonValue.toUpperCase());

      if (b.getCustomId().startsWith(prefix + "-lane-") && isActive) {
          try {
            this.laneType = LaneType.valueOf(buttonValue);
          } catch (Exception e) { }
      }
          

      if (b.getCustomId().startsWith(prefix + "-champion-")) {
          this.champion = ChampionUtils.getChampion(Integer.parseInt(buttonValue));
          this.showChampion = isActive;
      }
      
      if (b.getCustomId().startsWith(prefix + "-season-") && isActive)
          timeString = buttonValue;

      if (b.getCustomId().startsWith(prefix + "-leftpage")) 
          this.offset = Integer.parseInt(buttonValue);

      if (b.getCustomId().startsWith(prefix + "-change")) 
        fallbackChampion = Integer.parseInt(buttonValue);

      if (b.getCustomId().startsWith(prefix + "-settings-")) 
        this.filter = Filter.fromGenericKey(buttonValue);
      
    }

    if (this.champion == null && fallbackChampion > 0) 
      this.champion = ChampionUtils.getChampion(fallbackChampion);
    
    
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

  public LeagueMessageParameter withComponents(List<StringSelectMenu> menus) {
    for (StringSelectMenu menu : menus) {
      if (menu.getCustomId().equals("opgg-select")) {
        for (SelectOption option : menu.getOptions()) {
          if (option.isDefault()) {
            String gameId = option.getValue();
            String platform =  option.getValue().split("_")[0];
            LeagueShard shard = LeagueShard.valueOf(platform);
            this.match = LeagueHandler.getRiotApi().getLoLAPI().getMatchAPI().getMatch(shard.toRegionShard(), gameId);
          }
        }
        this.opggMenu = menu;
      }
      if (menu.getCustomId().equals("rank-select")) {
        this.livegameMenu = menu;
      }
    }
    return this;
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

  public int getShowingChampion() {
    return this.showChampion ? getChampionId() : 0;
  }

  public boolean isDuo() {
    return laneType == LaneType.BOT || laneType == LaneType.UTILITY || GameQueueTypeUtils.isCherry(queueType);
  }

  public long getTimeStart() {
    return this.period[0];
  }

  public long getTimeEnd() {
    return this.period[1];
  }

  public void setMatch(LOLMatch match) {
    this.match = match;
  }

  public LOLMatch getMatch() {
    return this.match;
  }

  public StringSelectMenu getLivegameMenu() {
    return this.livegameMenu;
  }

  public StringSelectMenu getOpggMenu() {
    return this.opggMenu;
  }

  public String getPatch() {
    return this.filter.patch();
  }

  public void setPatch(String patch) {
    this.filter.setPatch(patch);
  }

  public TierType getRank() {
    return this.filter.rank();
  }

  public void setRank(TierType rank) {
    this.filter.setRank(rank);
  }

  public LeagueShard getRegion() {
    return this.filter.region();
  }

  public void setRegion(LeagueShard region) {
    this.filter.setRegion(region);
  }

  public Filter toFilter() {
    return this.filter;
  }
}
