package com.safjnest.lol.model;

public class PlayerChampionStats {

    private static final double SCORE_WEIGHT_WINRATE = 2.0;
    private static final double SCORE_WEIGHT_KDA     = 1.5;
    private static final double SCORE_WEIGHT_GAMES   = 0.01;
    private static final double SCORE_WEIGHT_LP      = 0.05;

    private final int champion;
    private int kills;
    private int deaths;
    private int assists;
    private int wins;
    private int losses;
    private int lp;

    public PlayerChampionStats(int champion) {
        this.champion = champion;
    }

    public void add(int kills, int deaths, int assists, int lp, boolean win) {
        this.kills   += kills;
        this.deaths  += deaths;
        this.assists += assists;
        this.lp      += lp;
        if (win) wins++;
        else     losses++;
    }

    public int getChampion() { return champion; }
    public int getKills()    { return kills; }
    public int getDeaths()   { return deaths; }
    public int getAssists()  { return assists; }
    public int getWins()     { return wins; }
    public int getLosses()   { return losses; }
    public int getLp()       { return lp; }

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

    public double avgAssists() {
        int g = getGames();
        return g == 0 ? 0.0 : (double) assists / g;
    }

    public double avgKDA() {
        int g = getGames();
        if (g == 0) return 0.0;
        double denom = deaths == 0 ? 1.0 : (double) deaths;
        return (kills + assists) / denom;
    }

    public double winrate() {
        int g = getGames();
        return g == 0 ? 0.0 : (double) wins * 100.0 / g;
    }

    public int getScore() {
        double score =
            (winrate() * SCORE_WEIGHT_WINRATE) +
            (avgKDA()  * SCORE_WEIGHT_KDA) +
            (getGames() * SCORE_WEIGHT_GAMES) +
            (lp        * SCORE_WEIGHT_LP);

        if (Double.isNaN(score) || Double.isInfinite(score)) {
            return 0;
        }
        return (int) Math.round(score);
    }
}
