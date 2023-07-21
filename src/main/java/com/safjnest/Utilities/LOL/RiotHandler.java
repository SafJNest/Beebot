package com.safjnest.Utilities.LOL;

import java.net.URL;
import java.nio.charset.Charset;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.HashMap;
import java.util.Locale;

import org.apache.commons.io.IOUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import com.safjnest.Utilities.DatabaseHandler;
import com.safjnest.Utilities.LOL.Runes.PageRunes;
import com.safjnest.Utilities.LOL.Runes.Rune;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji;
import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.impl.R4J;
import no.stelar7.api.r4j.pojo.lol.championmastery.ChampionMastery;
import no.stelar7.api.r4j.pojo.lol.league.LeagueEntry;
import no.stelar7.api.r4j.pojo.lol.spectator.SpectatorParticipant;
import no.stelar7.api.r4j.pojo.lol.summoner.Summoner;


/**
 * This class is used to handle all the League of Legends related stuff
 * 
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 */

 public class RiotHandler {
    
    /**
     * The main object for make requests and get responses from the Riot API.
     */
    private static R4J riotApi;
    /**
     * An hashmap that contains all the runes ids and names.
     */
    private static HashMap<String, PageRunes> runesHandler = new HashMap<String, PageRunes>();

    /**
     * The current data dragon version.
     */
    private static String dataDragonVersion;

    //fammi il coso per likrare una pagina
    /**
     * url for get the suggested runes from lolanalytics.
     * @see {@link <a href="https://lolanalytics.com/">lolanalytics</a>}
     */
    private static String runesURL;

    /*
     * All the champions name in the game.
     */
    private static String[] champions;

    private static String[] ids = {"1106615853660766298", "1106615897952636930", "1106615926578761830", "1106615956685475991", "1106648612039041064", "1108673762708172811", "1117059269901164636", "1117060300592664677", "1117060763182452746", "1123678509693423738", "1131573980944416768"};
    
    public RiotHandler(R4J riotApi, String dataDragonVersion){
        RiotHandler.riotApi = riotApi;
        RiotHandler.dataDragonVersion = dataDragonVersion;
        RiotHandler.runesURL = "https://ddragon.leagueoflegends.com/cdn/" + RiotHandler.dataDragonVersion + "/data/en_US/runesReforged.json";
        
        loadChampions();
        System.out.println("[R4J-Runes] INFO Champions Successful! Thresh is ready to grab :)");

        loadRunes();
        System.out.println("[R4J-Runes] INFO Runes Successful! Ryze is happy :)");
    }

    /**
    * Useless method but {@link <a href="https://github.com/NeutronSun">NeutronSun</a>} is one
    * of the biggest bellsprout ever made
    */
	public void doSomethingSoSunxIsNotHurtBySeeingTheFuckingThingSayItsNotUsed() {
        return;
	}


    private void loadChampions(){
        champions = riotApi.getDDragonAPI().getChampions().values().stream().map(champ -> champ.getName()).toArray(String[]::new);
    }

    public static String[] getChampions(){
        return champions;
    }

    /**
     * Load all the runes data into {@link #runesHandler runesHandler}
     */
    private void loadRunes(){
        try {
            URL url = new URL(runesURL);
            String json = IOUtils.toString(url, Charset.forName("UTF-8"));
            JSONParser parser = new JSONParser();
            JSONArray file = (JSONArray) parser.parse(json);

            for(int i = 0; i < 5; i++){
                JSONObject page = (JSONObject)file.get(i);
                runesHandler.put(String.valueOf(page.get("id")), new PageRunes(
                    String.valueOf(page.get("id")),
                    String.valueOf(page.get("id")),
                    String.valueOf(page.get("key")),
                    String.valueOf(page.get("icon")),
                    String.valueOf(page.get("name"))
                ));
                JSONArray slots = (JSONArray)page.get("slots");
                for(int j=0; j<slots.size(); j++) {
                    JSONObject rowRunes = (JSONObject)slots.get(j);
                    JSONArray runes = (JSONArray)rowRunes.get("runes");
                    for(int k = 0; k < runes.size(); k++) {
                        JSONObject rune = (JSONObject)runes.get(k);
                        Rune r = new Rune(
                            String.valueOf(rune.get("id")),
                            String.valueOf(rune.get("key")),
                            String.valueOf(rune.get("icon")),
                            String.valueOf(rune.get("name")),
                            String.valueOf(rune.get("shortDesc")),
                            String.valueOf(rune.get("longDesc"))
                        );
                        runesHandler.get(String.valueOf(page.get("id"))).insertRune(r.getId(), r);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public static HashMap<String, PageRunes> getRunesHandler() {
        return runesHandler;
    } 

    public static R4J getRiotApi(){
        return riotApi;
    }


    public static Summoner getSummonerFromDB(String discordId){
        String query = "SELECT account_id FROM lol_user WHERE guild_id = '" + discordId + "';";
        try { 
            return riotApi.getLoLAPI().getSummonerAPI().getSummonerByAccount(LeagueShard.EUW1, DatabaseHandler.getSql().getString(query, "account_id")); 
        } catch (Exception e) { return null; }
    }


    public static String getAccountIdByName(String name){
        try {
            return riotApi.getLoLAPI().getSummonerAPI().getSummonerByName(LeagueShard.EUW1, name).getAccountId();
        } catch (Exception e) {
            return null;
        }
    }

    public static int getNumberOfProfile(String discordId){
        String query = "SELECT count(guild_id) as count FROM lol_user WHERE guild_id = '" + discordId + "';";
        try { 
            return Integer.valueOf(DatabaseHandler.getSql().getString(query, "count"));
        } catch (Exception e) { return 0; }
    }

    public static Summoner getSummonerByName(String nameAccount){
        try {
            return riotApi.getLoLAPI().getSummonerAPI().getSummonerByName(LeagueShard.EUW1, nameAccount);
        } catch (Exception e) { return null; }
    }

    public static Summoner getSummonerBySummonerId(String id){
        try { 
            return riotApi.getLoLAPI().getSummonerAPI().getSummonerById(LeagueShard.EUW1, id);
        } catch (Exception e) { return null; }
    }
    
    public static Summoner getSummonerByAccountId(String id){
        try { 
            return riotApi.getLoLAPI().getSummonerAPI().getSummonerByAccount(LeagueShard.EUW1, id);
        } catch (Exception e) { return null; }
    }

    public static String getSummonerProfilePic(Summoner s){
        return "https://ddragon.leagueoflegends.com/cdn/"+dataDragonVersion+"/img/profileicon/"+s.getProfileIconId()+".png";
    }

    public static String getChampionProfilePic(String champ){
        return "https://ddragon.leagueoflegends.com/cdn/"+dataDragonVersion+"/img/champion/"+champ+".png";
    }

    public static String getSoloQStats(JDA jda, Summoner s){
        String stats = "";
        for(int i = 0; i < 2; i++){
            try {
                LeagueEntry entry = riotApi.getLoLAPI().getLeagueAPI().getLeagueEntries(LeagueShard.EUW1, s.getSummonerId()).get(i);
                if(entry.getQueueType().commonName().equals("5v5 Ranked Solo"))
                    stats = getStatsByEntry(jda, entry);

            } catch (Exception e) { }
        }
        return (stats.equals("")) ? "Unranked" : stats;
    }

    public static String getFlexStats(JDA jda, Summoner s){
        String stats = "";
        for(int i = 0; i < 2; i++){
            try {
                LeagueEntry entry = riotApi.getLoLAPI().getLeagueAPI().getLeagueEntries(LeagueShard.EUW1, s.getSummonerId()).get(i);
                if(entry.getQueueType().commonName().equals("5v5 Ranked Flex Queue"))
                    stats = getStatsByEntry(jda, entry);
            } catch (Exception e) { }
        }
        return (stats.equals("")) ? "Unranked" : stats;
    }

    private static String getStatsByEntry(JDA jda, LeagueEntry entry){
        return getFormattedEmoji(jda, entry.getTier()) + " " + entry.getTier() + " " + entry.getRank()+ " " +String.valueOf(entry.getLeaguePoints()) + " LP\n"
        + entry.getWins() + "W/"+entry.getLosses()+"L\n"
        + "Winrate:" + Math.ceil((Double.valueOf(entry.getWins())/Double.valueOf(entry.getWins()+entry.getLosses()))*100)+"%";
    }

    public static String getMastery(JDA jda, Summoner s, int nChamp){
        DecimalFormat df = new DecimalFormat("#,##0", 
        new DecimalFormatSymbols(Locale.US));
        String masteryString = "";
        int cont = 1;
        try {
            for(ChampionMastery mastery : s.getChampionMasteries()){
                if(cont == nChamp){
                    masteryString += (mastery.getChampionLevel() > 4) ? RiotHandler.getFormattedEmoji(jda, "mastery" + mastery.getChampionLevel()) + " " : "";
                    masteryString +=  RiotHandler.getFormattedEmoji(jda, riotApi.getDDragonAPI().getChampion(mastery.getChampionId()).getName()) 
                                    + " **[" + mastery.getChampionLevel()+ "]** " 
                                    + riotApi.getDDragonAPI().getChampion(mastery.getChampionId()).getName() 
                                    + " " + df.format(mastery.getChampionPoints()) 
                                    + " points";
                    break;
                }
                cont++;
            }
            
        } catch (Exception e) { }
        return masteryString;
    }

    public static String getActivity(JDA jda, Summoner s){
        try {
            for(SpectatorParticipant partecipant : s.getCurrentGame().getParticipants()){
                if(partecipant.getSummonerId().equals(s.getSummonerId()))
                    return "Playing a " + s.getCurrentGame().getGameQueueConfig().commonName()+ " as " + getFormattedEmoji(jda, riotApi.getDDragonAPI().getChampion(partecipant.getChampionId()).getName()) + " " + riotApi.getDDragonAPI().getChampion(partecipant.getChampionId()).getName(); 
            }
        } catch (Exception e) {
            return "Not in a game";
        }
        return "Not in a game";
    }


    public static String getFormattedEmoji(JDA jda, String name){
        name = transposeChampionNameForDataDragon(name);
        try {    
            for(String id : ids){
                Guild g = jda.getGuildById(id);
                for(RichCustomEmoji em: g.getEmojisByName(name, true))
                    return "<:"+name+":"+em.getId()+">";
                
            } 
            if(name.equals("0"))
                return ":black_large_square:";
            return name;
        } catch (Exception e) {
            return null;
        }
    }

    public static String getEmojiId(JDA jda, String name){
        name = transposeChampionNameForDataDragon(name);
        try {    
            for(String id : ids){
                Guild g = jda.getGuildById(id);
                for(RichCustomEmoji em: g.getEmojisByName(name, true))
                    return em.getId();
                
            }   
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get the champion name that is more similar to the input such as "Kha'Zix" -> "Khazix"
     * @param champName
     * @return
     */
    public static String transposeChampionNameForDataDragon(String champName) {
        champName = champName.replace(".", "");
        champName = champName.replace("i'S", "is");
        champName = champName.replace("a'Z", "az");
        champName = champName.replace("l'K", "lk");
        champName = champName.replace("o'G", "og");
        champName = champName.replace("g'M", "gm");
        champName = champName.replace("'", "");
        champName = champName.replace(" & Willump", "");
        champName = champName.replace(" ", "");
        return champName;
    }

    /**
     * Get the champion name that is more similar to the input such as "mundo" -> "Dr. Mundo"
     * @param input
     * @return String
     */
    public static String findChampion(String input) {
        double maxSimilarity = 0;
        String championName = "";
        
        for (String champion : champions) {
            double similarity = calculateSimilarity(input, champion);
            if (similarity > maxSimilarity) {
                maxSimilarity = similarity;
                championName = champion;
            }
        }
        
        return championName;
    }
    
    private static double calculateSimilarity(String s1, String s2) {
        String longer = s1, shorter = s2;
        if (s1.length() < s2.length()) {
            longer = s2;
            shorter = s1;
        }
        int longerLength = longer.length();
        if (longerLength == 0) {
            return 1.0;
        }
        return (longerLength - editDistance(longer, shorter)) / (double) longerLength;
    }
    
    private static int editDistance(String s1, String s2) {
        s1 = s1.toLowerCase();
        s2 = s2.toLowerCase();
    
        int[] costs = new int[s2.length() + 1];
        for (int i = 0; i <= s1.length(); i++) {
            int lastValue = i;
            for (int j = 0; j <= s2.length(); j++) {
                if (i == 0) {
                    costs[j] = j;
                } else {
                    if (j > 0) {
                        int newValue = costs[j - 1];
                        if (s1.charAt(i - 1) != s2.charAt(j - 1)) {
                            newValue = Math.min(Math.min(newValue, lastValue), costs[j]) + 1;
                        }
                        costs[j - 1] = lastValue;
                        lastValue = newValue;
                    }
                }
            }
            if (i > 0) {
                costs[s2.length()] = lastValue;
            }
        }
        return costs[s2.length()];
    }

    public static String getFatherRune(String son){
        for(PageRunes page : runesHandler.values()){
            for(String id : page.getRunes().keySet()){
                if(id.equals(son))
                    return page.getName();
            }
        }
        return null;
    }
}
