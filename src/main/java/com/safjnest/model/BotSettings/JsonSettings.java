package com.safjnest.model.BotSettings;

public class JsonSettings {
    private AWSSettings amazonAWS;
    private DatabaseSettings postgreSQL;
    private DatabaseSettings mariaDB;
    private DatabaseSettings localHost;
    private DatabaseSettings database;
    private DatabaseSettings testDatabase;
    private DatabaseSettings testWebsiteDatabase;
    private DatabaseSettings leagueDatabase;
    private OpenAISettings openAI;
    private RiotSettings riot;
    private TwitchSettings twitch;
    private LavalinkSettings lavalink;
    private SpotifySettings spotify;

    private String youtubeApiKey;
    private String ttsApiKey;
    private String weatherApiKey;
    private String nasaApiKey;
    private String waitingTime;

    public AWSSettings getAmazonAWS() {
        return amazonAWS;
    }
    public void setAmazonAWS(AWSSettings amazonAWS) {
        this.amazonAWS = amazonAWS;
    }
    public DatabaseSettings getPostgreSQL() {
        return postgreSQL;
    }
    public void setPostgreSQL(DatabaseSettings postgreSQL) {
        this.postgreSQL = postgreSQL;
    }
    public DatabaseSettings getMariaDB() {
        return mariaDB;
    }
    public void setMariaDB(DatabaseSettings mariaDB) {
        this.mariaDB = mariaDB;
    }
    public DatabaseSettings getLocalHost() {
        return localHost;
    }
    public void setLocalHost(DatabaseSettings localHost) {
        this.localHost = localHost;
    }
    public DatabaseSettings getDatabase() {
        return database;
    }
    public void setDatabase(DatabaseSettings database) {
        this.database = database;
    }
    public DatabaseSettings getTestDatabase() {
        return testDatabase;
    }
    public void setTestDatabase(DatabaseSettings testDatabase) {
        this.testDatabase = testDatabase;
    }
    public DatabaseSettings getTestWebsiteDatabase() {
        return testWebsiteDatabase;
    }
    public void setTestWebsiteDatabase(DatabaseSettings testWebsiteDatabase) {
        this.testWebsiteDatabase = testWebsiteDatabase;
    }
    public DatabaseSettings getLeagueDatabase() {
        return leagueDatabase;
    }
    public void setLeagueDatabase(DatabaseSettings leagueDatabase) {
        this.leagueDatabase = leagueDatabase;
    }
    public OpenAISettings getOpenAI() {
        return openAI;
    }
    public void setOpenAI(OpenAISettings openAI) {
        this.openAI = openAI;
    }
    public RiotSettings getRiot() {
        return riot;
    }
    public void setRiot(RiotSettings riot) {
        this.riot = riot;
    }
    public TwitchSettings getTwitch() {
        return twitch;
    }
    public void setTwitch(TwitchSettings twitch) {
        this.twitch = twitch;
    }
    public LavalinkSettings getLavalink() {
        return lavalink;
    }
    public void setLavalink(LavalinkSettings lavalink) {
        this.lavalink = lavalink;
    }
    public SpotifySettings getSpotify() {
        return spotify;
    }
    public void setSpotify(SpotifySettings spotify) {
        this.spotify = spotify;
    }
    public String getYoutubeApiKey() {
        return youtubeApiKey;
    }
    public void setYoutubeApiKey(String youtubeApiKey) {
        this.youtubeApiKey = youtubeApiKey;
    }
    public String getTtsApiKey() {
        return ttsApiKey;
    }
    public void setTtsApiKey(String ttsApiKey) {
        this.ttsApiKey = ttsApiKey;
    }
    public String getWeatherApiKey() {
        return weatherApiKey;
    }
    public void setWeatherApiKey(String weatherApiKey) {
        this.weatherApiKey = weatherApiKey;
    }
    public String getNasaApiKey() {
        return nasaApiKey;
    }
    public void setNasaApiKey(String nasaApiKey) {
        this.nasaApiKey = nasaApiKey;
    }
    public String getWaitingTime() {
        return waitingTime;
    }
    public void setWaitingTime(String waitingTime) {
        this.waitingTime = waitingTime;
    }
}
