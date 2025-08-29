package com.safjnest.util.lol;

public class ParticipantChampionStat {
  private int champion;
  private int kills;
  private int deaths;
  private int assist;
  private int wins;
  private int losses;
  private int lp;

  public ParticipantChampionStat(int champion) {
    this.champion = champion;
    kills = 0;
    deaths = 0;
    assist = 0;
    wins = 0;
    losses = 0;
    lp = 0;
  }

  public void add(int kills, int deaths, int assist, int lp, boolean win) {
    this.kills += kills;
    this.deaths += deaths;
    this.assist += assist;
    this.lp += lp;
    if (win) wins++;
    else losses++;
  }

  public int getKills() { return kills; }
  public int getDeaths() { return deaths; }
  public int getAssist() { return assist; }
  public int getWins() { return wins; }
  public int getLossess() { return losses; }
  public int getLp() { return lp; }
  public int getChampion() { return champion; }

  public int getGames() {
    return wins + losses;
  }

  public double avgKills() {
    int g = getGames();
    return g == 0 ? 0.0 : (double) kills / g;
  }

  public double avgDeaths() {
    int g = getGames();
    return g == 0 ? 0.0 : (double) deaths / g;
  }

  public double avgAssist() {
    int g = getGames();
    return g == 0 ? 0.0 : (double) assist / g;
  }

  /**
   * KDA calcolata come (kills + assist) / max(1, deaths)
   * (evita divisione per zero; se vuoi la media per partita cambia la formula)
   */
  public double avgKDA() {
    int g = getGames();
    if (g == 0) return 0.0;
    int totalDeaths = deaths;
    double denom = totalDeaths == 0 ? 1.0 : (double) totalDeaths;
    return (double) (kills + assist) / denom;
  }

  /** Percentuale di vittorie (0..100) */
  public double winrate() {
    int g = getGames();
    return g == 0 ? 0.0 : (double) wins * 100.0 / g;
  }

}
