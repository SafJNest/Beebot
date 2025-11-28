package com.safjnest.lol.message;

public enum LeagueMessageType {
  PROFILE("Profile", false),
  OPGG("OPgg", true, 5),
  LIVEGAME("Live Game", false),
  OVERVIEW("Overview", false),
  MATCHUP("Matchups", false),
  OVERVIEW_PING("Pings", false),
  OVERVIEW_OBJECTIVES("Objectives", false),
  OVERVIEW_CHAMPIONS("Champions", true, 10),
  OVERVIEW_OPGG("OPgg", true, 5);

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
