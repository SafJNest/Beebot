package com.safjnest.util.lol;

public enum LeagueMessageType {
  CHAMPION_OVERVIEW("Overview", false),
  CHAMPION_MATCHUP("Matchups", false),
  CHAMPION_PING("Pings", false),
  CHAMPION_OBJECTIVES("Objectives", false),
  CHAMPION_CHAMPIONS("Champions", true, 10),
  CHAMPION_OPGG("OPgg", true, 5);

  private String label;
  private boolean hasPageButton;
  private int pageItem;

  private LeagueMessageType(String label, boolean hasPageButton) {
    this.label = label;
    this.hasPageButton = hasPageButton;
  }

  private LeagueMessageType(String label, boolean hasPageButton, int pageItem) {
    this.label = label;
    this.hasPageButton = hasPageButton;
    this.pageItem = pageItem;
  }

  public String getLabel() {
    return this.label;
  }

  public boolean hasPageButtons() {
    return this.hasPageButton;
  }

  public int getPageItem() {
    return this.pageItem;
  }
}
