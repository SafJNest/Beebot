package com.safjnest.util.LOL;

import java.io.File;
import java.io.FileReader;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

import org.apache.commons.io.IOUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.core.Bot;
import com.safjnest.model.customemoji.CustomEmojiHandler;
import com.safjnest.model.guild.GuildData;
import com.safjnest.sql.DatabaseHandler;
import com.safjnest.sql.ResultRow;
import com.safjnest.util.LOL.Runes.PageRunes;
import com.safjnest.util.LOL.Runes.Rune;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.api.regions.RegionShard;
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

    private static ArrayList<AugmentData> augments = new ArrayList<>();

    public RiotHandler(R4J riotApi, String dataDragonVersion){
        RiotHandler.riotApi = riotApi;
        RiotHandler.dataDragonVersion = dataDragonVersion;
        RiotHandler.runesURL = "https://ddragon.leagueoflegends.com/cdn/" + RiotHandler.dataDragonVersion + "/data/en_US/runesReforged.json";
        
        loadChampions();
        System.out.println("[R4J-Champions] INFO Champions Successful! Thresh is ready to grab :)");

        loadRunes();
        System.out.println("[R4J-Runes] INFO Runes Successful! Ryze is happy :)");

        loadAguments();
        System.out.println("[R4J-Augments] INFO Augments Successful! Viktor is proud :)");
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
            URI uri = new URI(runesURL);
            URL url = uri.toURL();
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

    private void loadAguments(){
        try {
            FileReader reader = new FileReader("rsc" + File.separator + "Testing" + File.separator + "lol_testing" + File.separator + "augments.json");
            JSONParser parser = new JSONParser();
            JSONObject file = (JSONObject) parser.parse(reader);
            for(int i = 1; i < 99; i++){
                if(i == 3) continue;

                JSONObject augment = (JSONObject)file.get(String.valueOf(i));

                HashMap<String, String> spellDataValues = new HashMap<>();
                JSONObject spellData = (JSONObject)augment.get("spellDataValues");
                for(Object key : spellData.keySet()){
                    spellDataValues.put(String.valueOf(key), String.valueOf(spellData.get(key)));
                }
                augments.add(new AugmentData(
                    String.valueOf(augment.get("id")),
                    String.valueOf(augment.get("displayName")),
                    String.valueOf(augment.get("tooltip")),
                    spellDataValues
                ));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static HashMap<String, PageRunes> getRunesHandler() {
        return runesHandler;
    } 

    public static ArrayList<AugmentData> getAugments() {
        return augments;
    }

    public static R4J getRiotApi(){
        return riotApi;
    }


    public static Summoner getSummonerFromDB(String discordId){
        try {
            ResultRow account = DatabaseHandler.getLOLAccountIdByUserId(discordId);
            LeagueShard shard = LeagueShard.values()[Integer.valueOf(account.get("league_shard"))];

            return riotApi.getLoLAPI().getSummonerAPI().getSummonerByAccount(shard, account.get("account_id"));
        } catch (Exception e) {return null;}
    }


    public static int getNumberOfProfile(String discordId){
        try { 
            return Integer.valueOf(DatabaseHandler.getLolProfilesCount(discordId));
        } catch (Exception e) { return 0; }
    }

    public static Summoner getSummonerByName(String nameAccount, String tag, LeagueShard shard){
        try {
            String puiid = riotApi.getAccountAPI().getAccountByTag(getRegionFromServer(shard), nameAccount, tag).getPUUID();
            return riotApi.getLoLAPI().getSummonerAPI().getSummonerByPUUID(shard, puiid);  
        } catch (Exception e) {
            return null;
        }
    }

    public static RegionShard getRegionFromServer(LeagueShard shard) {
        return shard.toRegionShard();
    }

    public static LeagueShard getShardFromOrdinal(int ordinal){
        return LeagueShard.values()[ordinal];
    }



    public static Summoner getSummonerBySummonerId(String id, LeagueShard shard){
        try { 
            return riotApi.getLoLAPI().getSummonerAPI().getSummonerById(shard, id);
        } catch (Exception e) {return null; }
    }
    
    public static Summoner getSummonerByAccountId(String id, LeagueShard shard){
        try { 
            return riotApi.getLoLAPI().getSummonerAPI().getSummonerByAccount(shard, id);
        } catch (Exception e) { return null; }
    }

    public static String getSummonerProfilePic(Summoner s){
        return "https://ddragon.leagueoflegends.com/cdn/"+dataDragonVersion+"/img/profileicon/"+s.getProfileIconId()+".png";
    }

    public static String getSummonerProfilePic(int id){
        return "https://ddragon.leagueoflegends.com/cdn/"+dataDragonVersion+"/img/profileicon/"+id+".png";
    }

    public static String getChampionProfilePic(String champ){
        return "https://ddragon.leagueoflegends.com/cdn/"+dataDragonVersion+"/img/champion/"+champ+".png";
    }

    public static String getSoloQStats(JDA jda, Summoner s){
        String stats = "";
        for(int i = 0; i < 2; i++){
            try {
                LeagueEntry entry = riotApi.getLoLAPI().getLeagueAPI().getLeagueEntries(s.getPlatform(), s.getSummonerId()).get(i);
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
                LeagueEntry entry = riotApi.getLoLAPI().getLeagueAPI().getLeagueEntries(s.getPlatform(), s.getSummonerId()).get(i);
                if(entry.getQueueType().commonName().equals("5v5 Ranked Flex Queue"))
                    stats = getStatsByEntry(jda, entry);
            } catch (Exception e) { }
        }
        return (stats.equals("")) ? "Unranked" : stats;
    }

    private static String getStatsByEntry(JDA jda, LeagueEntry entry){
        return CustomEmojiHandler.getFormattedEmoji(entry.getTier()) + " " + entry.getTier() + " " + entry.getRank()+ " " +String.valueOf(entry.getLeaguePoints()) + " LP\n"
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
                    int level = mastery.getChampionLevel() >= 10 ? 10 : mastery.getChampionLevel();
                    masteryString += CustomEmojiHandler.getFormattedEmoji("mastery" + level) + " ";
                    masteryString +=  CustomEmojiHandler.getFormattedEmoji(riotApi.getDDragonAPI().getChampion(mastery.getChampionId()).getName()) 
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
                    return "Playing a " + s.getCurrentGame().getGameQueueConfig().commonName()+ " as " + CustomEmojiHandler.getFormattedEmoji(riotApi.getDDragonAPI().getChampion(partecipant.getChampionId()).getName()) + " " + riotApi.getDDragonAPI().getChampion(partecipant.getChampionId()).getName(); 
            }
        } catch (Exception e) {
            return "Not in a game";
        }
        return "Not in a game";
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

    public static String getFatherRune(String son){
        for(PageRunes page : runesHandler.values()){
            for(String id : page.getRunes().keySet()){
                if(id.equals(son))
                    return page.getName();
            }
        }
        return null;
    }

    public static String getFatherRune(int son){
        for(PageRunes page : runesHandler.values()){
            for(String id : page.getRunes().keySet()){
                if(id.equals(String.valueOf(son)))
                    return page.getName();
            }
        }
        return null;
    }

    public static Summoner getSummonerByArgs(CommandEvent event) {
        String args = event.getArgs();
        GuildData guild = Bot.getGuildData(event.getGuild().getId());

        if (args.isEmpty()) {
            return getSummonerFromDB(event.getAuthor().getId());
        }

        if (event.getMessage().getMentions().getMembers().size() != 0) {
            return getSummonerFromDB(event.getMessage().getMentions().getMembers().get(0).getId());
        }

        String name = "";
        String tag = "";
        if (!args.contains("#")) {
            name = args;
            tag = "EUW";
        } else {
            name = args.split("#", 2)[0];
            tag = args.split("#", 2)[1];
        }

        return getSummonerByName(name, tag, guild.getLeagueShard());
    }

    public static Summoner getSummonerByArgs(SlashCommandEvent event) {
        GuildData guild = Bot.getGuildData(event.getGuild().getId());
        
        if (event.getOption("summoner") == null && event.getOption("user") == null) {
            return getSummonerFromDB(event.getUser().getId());
        }

        if (event.getOption("user") != null) {
            return getSummonerFromDB(event.getOption("user").getAsUser().getId());
        }

        LeagueShard shard = event.getOption("region") != null ? getShardFromOrdinal(Integer.valueOf(event.getOption("region").getAsString())) : guild.getLeagueShard();
        String name = event.getOption("summoner").getAsString();
        String tag = (event.getOption("tag") != null) ? event.getOption("tag").getAsString() : shard.name();
        return getSummonerByName(name, tag, shard);
    }

    public static OptionData getLeagueShardOptions(boolean required) {
        Choice[] choices = new Choice[LeagueShard.values().length];
        for (int i = 0; i < LeagueShard.values().length; i++) {
            choices[i] = new Choice(LeagueShard.values()[i].commonName(), String.valueOf(i));
        }


        return new OptionData(OptionType.STRING, "region", "Region you want to get the summoner from", required)
                    .addChoices(choices);
                    
    }

    public static OptionData getLeagueShardOptions() {
        return getLeagueShardOptions(false);
    }

    public static double getKDA(int kills, int deaths, int assists){
        double kda = 0;
        if (deaths == 0)
            kda =  kills + assists;
        kda =  (double) (kills + assists) / deaths;
        return Math.round(kda * 100.0) / 100.0;
    }
}
