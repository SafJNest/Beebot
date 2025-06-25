package com.safjnest.util.lol;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.model.UserData;
import com.safjnest.model.customemoji.CustomEmojiHandler;
import com.safjnest.model.guild.GuildData;
import com.safjnest.sql.LeagueDBHandler;
import com.safjnest.util.SafJNest;
import com.safjnest.util.SettingsLoader;
import com.safjnest.util.log.BotLogger;
import com.safjnest.util.lol.Runes.PageRunes;
import com.safjnest.util.lol.Runes.Rune;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import no.stelar7.api.r4j.basic.APICredentials;
import no.stelar7.api.r4j.basic.calling.DataCall;
import no.stelar7.api.r4j.basic.constants.api.URLEndpoint;
import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierDivisionType;
import no.stelar7.api.r4j.impl.R4J;
import no.stelar7.api.r4j.pojo.lol.championmastery.ChampionMastery;
import no.stelar7.api.r4j.pojo.lol.league.LeagueEntry;
import no.stelar7.api.r4j.pojo.lol.match.v5.LOLMatch;
import no.stelar7.api.r4j.pojo.lol.spectator.SpectatorGameInfo;
import no.stelar7.api.r4j.pojo.lol.spectator.SpectatorParticipant;
import no.stelar7.api.r4j.pojo.lol.staticdata.champion.StaticChampion;
import no.stelar7.api.r4j.pojo.lol.summoner.Summoner;
import no.stelar7.api.r4j.pojo.shared.RiotAccount;

import com.safjnest.core.cache.managers.GuildCache;
import com.safjnest.core.cache.managers.UserCache;


/**
 * This class is used to handle all the League of Legends related stuff
 *
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 */

 public class LeagueHandler {

    private static R4J riotApi;

    private static String version;

    private static String runesURL;

    private static String[] champions;

    private static HashMap<String, PageRunes> runesHandler = new HashMap<String, PageRunes>();
    private static ArrayList<AugmentData> augments = new ArrayList<>();

    static {
        try {
            LeagueHandler.riotApi = new R4J(new APICredentials(SettingsLoader.getSettings().getJsonSettings().getRiot().getKey()));
            BotLogger.info("[R4J] Connection Successful!");
        } catch (Exception e) {
            BotLogger.error("[R4J] Annodam Not Successful!");
        }
        
        LeagueHandler.version = getVersion();
        LeagueHandler.runesURL = "https://ddragon.leagueoflegends.com/cdn/" + LeagueHandler.version + "/data/en_US/runesReforged.json";

        loadChampions();
        loadRunes();
        loadAguments();
        new MatchTracker();
    }

    public static String getVersion() {
        if (version == null) {
            try {
                URI uri = new URI("https://ddragon.leagueoflegends.com/api/versions.json");
                URL url = uri.toURL();
                String json = IOUtils.toString(url, Charset.forName("UTF-8"));
                JSONParser parser = new JSONParser();
                JSONArray file = (JSONArray) parser.parse(json);

                // Get the latest version (first element in the array)
                version = (String) file.get(0);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return version;
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

    public static HashMap<Integer, Integer> getTierDivision() {
        HashMap<Integer, Integer> tierDivisionMapReformed = new HashMap<>();
        List<TierDivisionType> tierDivisionList = List.of(TierDivisionType.values())
            .stream()
            .filter(t -> !t.name().endsWith("_V"))
            .collect(Collectors.toList());

        TierDivisionType[] tierDivisionTypesArray = tierDivisionList.toArray(new TierDivisionType[tierDivisionList.size()]);

        for (int i = tierDivisionTypesArray.length - 1, value = 0; i >= 0; i--, value += 100) {
            tierDivisionMapReformed.put(tierDivisionTypesArray[i].ordinal(), value);
        }

        return tierDivisionMapReformed;

    }

    public static String convertSpellToId(String name) {
        String id = "";
        switch (name) {
            case "Flash":
                id = "4";
            break;
            case "Heal":
                id = "7";
            break;
            case "Barrier":
                id = "21";
            break;
            case "Ghost":
                id = "6";
            break;
            case "Exhaust":
                id = "3";
            break;
            case "Teleport":
                id = "12";
            break;
            case "Ignite":
                id = "14";
            break;
            case "Cleanse":
                id = "1";
            break;
            case "Smite":
                id = "11";
            break;
            case "Mark":
                id = "32";
            break;
            case "Dash":
                id = "30";
            break;
            case "Clarity":
                id = "13";
            break;
            case "Garrison":
                id = "17";
            break;
            case "To the King!":
                id = "31";
            break;
        }
        return id;
    }

    public static double getKDA(int kills, int deaths, int assists){
        double kda = 0;
        if (deaths == 0)
            kda =  kills + assists;
        kda =  (double) (kills + assists) / deaths;
        return Math.round(kda * 100.0) / 100.0;
    }

    public static String formatMatchName(GameQueueType queue) {
        String name = queue.commonName();
        switch (queue) {
            case CHERRY:
                name = "Arena";
                break;
            case STRAWBERRY:
                name = "Swarm";
                break;
            case ULTBOOK:
                name = "Ultimate Spellbook";
                break;
            case SWIFTPLAY:
                name = "Swiftplay";
                break;
            case URF:
            case ALL_RANDOM_URF:
                name = "URF";
                break;
            default:
                break;
        }
        if (queue.commonName().equals("5v5 Ranked Solo")) name = "Ranked Solo/Duo";
        else if (queue.commonName().equals("5v5 Ranked Flex Queue")) name = "Ranked Flex";
        else if (queue.commonName().equals("5v5 Draft Pick")) name = "Draft Pick";

        return name;
    }

    public static String getLaneTypeEmoji(LaneType type) {
        String emoji = "";
        switch (type) {
            case TOP:
                emoji = CustomEmojiHandler.getFormattedEmoji("TopLane");
                break;
            case JUNGLE:
                emoji = CustomEmojiHandler.getFormattedEmoji("Jungle");
                break;
            case MID:
                emoji = CustomEmojiHandler.getFormattedEmoji("MidLane");
                break;
            case BOT:
                emoji = CustomEmojiHandler.getFormattedEmoji("ADC");
                break;
            case UTILITY:
                emoji = CustomEmojiHandler.getFormattedEmoji("Support");
                break;
            case NONE:
                emoji = CustomEmojiHandler.getFormattedEmoji("autofill");
                break;
            default:
                break;
        }
        return emoji;
    }

    public static String getMapEmoji(GameQueueType type) {
        String emoji = "";
        switch (type) {
            case CHERRY:
            case STRAWBERRY:
            case NEXUS_BLITZ:
                emoji = CustomEmojiHandler.getFormattedEmoji("arena_mode");
                break;
            case TEAM_BUILDER_RANKED_SOLO:
            case RANKED_FLEX_SR:
            case TEAM_BUILDER_DRAFT_UNRANKED_5X5:
            case QUICKPLAY_NORMAL:
            case SWIFTPLAY:
            case NORMAL_5V5_BLIND_PICK:
                emoji = CustomEmojiHandler.getFormattedEmoji("rift_mode");
                break;
            case ULTBOOK:
            case URF:
            case ALL_RANDOM_URF:
            case ONEFORALL_5X5:
                emoji = CustomEmojiHandler.getFormattedEmoji("special_mode");
                break;
            case ARAM:
            case ARAM_CLASH:
                emoji = CustomEmojiHandler.getFormattedEmoji("bridge_mode");
                break;
            default:
                break;
        }
        return emoji;
    }

    public static String getPrettyName(LaneType type) {
        String name = "";
        switch (type) {
            case TOP:
                name = "Top Lane";
                break;
            case JUNGLE:
                name = "Jungle";
                break;
            case MID:
                name = "Mid Lane";
                break;
            case BOT:
                name = "Bot Lane";
                break;
            case UTILITY:
                name = "Support";
                break;
            case NONE:
                name = "Remake Or NoLane";
                break;
            default:
                name = type.name();
                break;
        }
        return name;
    }

    public static boolean isHighElo(TierDivisionType division) {
        return Arrays.asList(TierDivisionType.MASTER_I, TierDivisionType.GRANDMASTER_I, TierDivisionType.CHALLENGER_I).contains(division);
    }

//   ▄█        ▄██████▄     ▄████████ ████████▄           ███        ▄█    █▄     ▄█  ███▄▄▄▄      ▄██████▄     ▄████████
//  ███       ███    ███   ███    ███ ███   ▀███      ▀█████████▄   ███    ███   ███  ███▀▀▀██▄   ███    ███   ███    ███
//  ███       ███    ███   ███    ███ ███    ███         ▀███▀▀██   ███    ███   ███▌ ███   ███   ███    █▀    ███    █▀
//  ███       ███    ███   ███    ███ ███    ███          ███   ▀  ▄███▄▄▄▄███▄▄ ███▌ ███   ███  ▄███          ███
//  ███       ███    ███ ▀███████████ ███    ███          ███     ▀▀███▀▀▀▀███▀  ███▌ ███   ███ ▀▀███ ████▄  ▀███████████
//  ███       ███    ███   ███    ███ ███    ███          ███       ███    ███   ███  ███   ███   ███    ███          ███
//  ███▌    ▄ ███    ███   ███    ███ ███   ▄███          ███       ███    ███   ███  ███   ███   ███    ███    ▄█    ███
//  █████▄▄██  ▀██████▀    ███    █▀  ████████▀          ▄████▀     ███    █▀    █▀    ▀█   █▀    ████████▀   ▄████████▀
//  ▀

    /**
     * Load all the runes data into {@link #runesHandler runesHandler}
     */
    private static void loadRunes(){
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

    private static void loadAguments() {
        try {
            FileReader reader = new FileReader("rsc" + File.separator + "Testing" + File.separator + "lol_testing" + File.separator + "augments.json");
            JSONParser parser = new JSONParser();
            JSONObject file = (JSONObject) parser.parse(reader);
            JSONArray augmentsArray = (JSONArray) file.get("augments");

            for (Object obj : augmentsArray) {
                JSONObject augment = (JSONObject) obj;

                HashMap<String, String> spellDataValues = new HashMap<>();
                JSONObject spellData = (JSONObject) augment.get("dataValues");
                for (Object key : spellData.keySet()) {
                    spellDataValues.put(String.valueOf(key), String.valueOf(spellData.get(key)));
                }
                augments.add(new AugmentData(
                    String.valueOf(augment.get("id")),
                    String.valueOf(augment.get("name")),
                    String.valueOf(augment.get("tooltip")),
                    spellDataValues
                ));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

//     ▄████████ ███    █▄    ▄▄▄▄███▄▄▄▄     ▄▄▄▄███▄▄▄▄    ▄██████▄  ███▄▄▄▄      ▄████████    ▄████████
//    ███    ███ ███    ███ ▄██▀▀▀███▀▀▀██▄ ▄██▀▀▀███▀▀▀██▄ ███    ███ ███▀▀▀██▄   ███    ███   ███    ███
//    ███    █▀  ███    ███ ███   ███   ███ ███   ███   ███ ███    ███ ███   ███   ███    █▀    ███    ███
//    ███        ███    ███ ███   ███   ███ ███   ███   ███ ███    ███ ███   ███  ▄███▄▄▄      ▄███▄▄▄▄██▀
//  ▀███████████ ███    ███ ███   ███   ███ ███   ███   ███ ███    ███ ███   ███ ▀▀███▀▀▀     ▀▀███▀▀▀▀▀
//           ███ ███    ███ ███   ███   ███ ███   ███   ███ ███    ███ ███   ███   ███    █▄  ▀███████████
//     ▄█    ███ ███    ███ ███   ███   ███ ███   ███   ███ ███    ███ ███   ███   ███    ███   ███    ███
//   ▄████████▀  ████████▀   ▀█   ███   █▀   ▀█   ███   █▀   ▀██████▀   ▀█   █▀    ██████████   ███    ███
//                                                                                              ███    ███

    /**
     * After like 2 years I realized that this method COULD save me a lot of time.
     * <br>
     * safj
     */
    public static RiotAccount getRiotAccountFromSummoner(Summoner s){
        return riotApi.getAccountAPI().getAccountByPUUID(s.getPlatform().toRegionShard(), s.getPUUID());
    }


    /**
     * @deprecated
     * @param discordId
     * @return
     */
    public static Summoner getSummonerFromDB(String discordId){
        // try {
        //     ResultRow account = DatabaseHandler.getLOLAccountIdByUserId(discordId);
        //     LeagueShard shard = LeagueShard.values()[Integer.valueOf(account.get("league_shard"))];

        //     return riotApi.getLoLAPI().getSummonerAPI().getSummonerByAccount(shard, account.get("account_id"));
        // } catch (Exception e) {return null;}
        return getSummonerByUserData(UserCache.getUser(discordId));
    }

    public static Summoner getSummonerByUserData(UserData user){
        try {
            HashMap<String, String> accounts = user.getRiotAccounts();
            if (accounts == null || accounts.size() == 0) return null;

            String firstAccount = accounts.keySet().stream().findFirst().get();
            LeagueShard shard = LeagueShard.values()[Integer.valueOf(accounts.get(firstAccount))];

            return riotApi.getLoLAPI().getSummonerAPI().getSummonerByPUUID(shard, firstAccount);
        } catch (Exception e) {return null;}
    }

    public static int getNumberOfProfile(String userId){
        try {
            return UserCache.getUser(userId).getRiotAccounts().size();
        } catch (Exception e) { return 0; }
    }

    public static Summoner getSummonerByName(String nameAccount, String tag, LeagueShard shard) {
        try {
            String puiid = riotApi.getAccountAPI().getAccountByTag(shard.toRegionShard(), nameAccount, tag).getPUUID();
            return riotApi.getLoLAPI().getSummonerAPI().getSummonerByPUUID(shard, puiid);
        } catch (Exception e) {
            return null;
        }
    }

    public static Summoner getSummonerByPuuid(String id, LeagueShard shard){
        try {
            return riotApi.getLoLAPI().getSummonerAPI().getSummonerByPUUID(shard, id);
        } catch (Exception e) { return null; }
    }

    public static Summoner getSummonerByArgs(CommandEvent event) {
        String args = event.getArgs();
        GuildData guild = GuildCache.getGuildOrPut(event.getGuild().getId());

        if (args.isEmpty()) {
            return getSummonerFromDB(event.getAuthor().getId());
        }

        if (event.getMessage().getMentions().getMembers().size() != 0) {
            return getSummonerFromDB(event.getMessage().getMentions().getMembers().get(0).getId());
        }

        if (SafJNest.longIsParsable(args) && event.getJDA().getUserById(args) != null) {
            return getSummonerFromDB(args);
        }

        args = args.replaceAll("[\\p{C}]", ""); //when you copy the name from riot chat it adds some weird characters

        String name = "";
        String tag = "";
        if (!args.contains("#")) {
            name = args;
            tag = getRegionCode(guild.getLeagueShard(event.getChannel().getId()));
        } else {
            name = args.split("#", 2)[0];
            tag = args.split("#", 2)[1];
        }
        return getSummonerByName(name, tag, guild.getLeagueShard(event.getChannel().getId()));
    }

    public static Summoner getSummonerByArgs(SlashCommandEvent event) {
        GuildData guild = event.isFromGuild() ? GuildCache.getGuildOrPut(event.getGuild().getId()) : null;


        User user = event.getOption("user") != null ? event.getOption("user").getAsUser() : event.getUser();

        Summoner s = null;

        if (event.getOption("summoner") == null && (s = getSummonerFromDB(user.getId())) != null) {
            return s;
        }

        LeagueShard guildShard = event.isFromGuild()  ? guild.getLeagueShard(event.getChannel().getId()) : LeagueShard.EUW1;
        LeagueShard shard = event.getOption("region") != null ? getShardFromOrdinal(Integer.valueOf(event.getOption("region").getAsString())) : guildShard;

        String summoner = event.getOption("summoner").getAsString().replaceAll("[\\p{C}]", ""); //when you copy the name from riot chat it adds some weird characters
        String tag = summoner.contains("#") ? summoner.split("#", 2)[1] : getRegionCode(shard);
        String name = summoner.contains("#") ? summoner.split("#", 2)[0] : summoner;

        return getSummonerByName(name, tag, shard);
    }

    public static String getFormattedSummonerName(Summoner s) {
        RiotAccount account = riotApi.getAccountAPI().getAccountByPUUID(s.getPlatform().toRegionShard(), s.getPUUID());
        if (account == null) return "";
        return account.getName() + "#" + account.getTag();
    }

    public static int updateSummonerDB(Summoner summoner) {
        return LeagueDBHandler.addLOLAccount(summoner);
    }

    public static void updateSummonerDB(SpectatorGameInfo game) {
        LeagueDBHandler.addLOLAccount(game);
    }

    public static void updateSummonerDB(LOLMatch match) {
        LeagueDBHandler.addLOLAccountFromMatch(match);
    }

//     ▄███████▄  ▄█   ▄████████
//    ███    ███ ███  ███    ███
//    ███    ███ ███▌ ███    █▀
//    ███    ███ ███▌ ███
//  ▀█████████▀  ███▌ ███
//    ███        ███  ███    █▄
//    ███        ███  ███    ███
//   ▄████▀      █▀   ████████▀
//

    public static String getSummonerProfilePic(Summoner s){
        return "https://ddragon.leagueoflegends.com/cdn/"+version+"/img/profileicon/"+s.getProfileIconId()+".png";
    }

    public static String getSummonerProfilePic(int id){
        return "https://ddragon.leagueoflegends.com/cdn/"+version+"/img/profileicon/"+id+".png";
    }

    public static String getChampionProfilePic(String champ){
        return "https://ddragon.leagueoflegends.com/cdn/"+version+"/img/champion/"+champ+".png";
    }

    public static String getChampionProfilePic(int champ, String skin){
        return "https://cdn.communitydragon.org/"+version+"/champion/"+champ+"/tile/skin/" + skin;
    }

//     ▄████████ ███▄▄▄▄       ███        ▄████████ ▄██   ▄
//    ███    ███ ███▀▀▀██▄ ▀█████████▄   ███    ███ ███   ██▄
//    ███    █▀  ███   ███    ▀███▀▀██   ███    ███ ███▄▄▄███
//   ▄███▄▄▄     ███   ███     ███   ▀  ▄███▄▄▄▄██▀ ▀▀▀▀▀▀███
//  ▀▀███▀▀▀     ███   ███     ███     ▀▀███▀▀▀▀▀   ▄██   ███
//    ███    █▄  ███   ███     ███     ▀███████████ ███   ███
//    ███    ███ ███   ███     ███       ███    ███ ███   ███
//    ██████████  ▀█   █▀     ▄████▀     ███    ███  ▀█████▀
//                                       ███    ███

    public static String getSoloQStats(Summoner s){
        String stats = "";
        for(int i = 0; i < 3; i++){
            try {
                LeagueEntry entry = riotApi.getLoLAPI().getLeagueAPI().getLeagueEntriesByPUUID(s.getPlatform(), s.getPUUID()).get(i);
                if(entry.getQueueType().commonName().equals("5v5 Ranked Solo"))
                    stats = getStatsByEntry(entry);

            } catch (Exception e) {}
        }
        return (stats.equals("")) ? (CustomEmojiHandler.getFormattedEmoji("Unranked") + " Unranked") : stats;
    }

    public static String getFlexStats(Summoner s){
        String stats = "";
        for(int i = 0; i < 3; i++){
            try {
                LeagueEntry entry = riotApi.getLoLAPI().getLeagueAPI().getLeagueEntriesByPUUID(s.getPlatform(), s.getPUUID()).get(i);
                if(entry.getQueueType().commonName().equals("5v5 Ranked Flex Queue"))
                    stats = getStatsByEntry(entry);
            } catch (Exception e) { }
        }
        return (stats.equals("")) ? (CustomEmojiHandler.getFormattedEmoji("Unranked") + " Unranked") : stats;
    }  

    private static String getStatsByEntry(LeagueEntry entry){
        return CustomEmojiHandler.getFormattedEmoji(entry.getTier()) + " " + entry.getTier() + " " + entry.getRank() + " " + entry.getLeaguePoints() + " LP\n"
        + "`(" + entry.getWins() + "W/"+entry.getLosses()+"L) - " + Math.ceil((Double.valueOf(entry.getWins())/Double.valueOf(entry.getWins()+entry.getLosses()))*100) + "% WR`";

    }

    public static LeagueEntry getRankEntry(String summonerId, LeagueShard shard) {
        try {
            for(int i = 0; i < 3; i++){
                LeagueEntry entry = riotApi.getLoLAPI().getLeagueAPI().getLeagueEntriesByPUUID(shard, summonerId).get(i);
                if(entry.getQueueType().commonName().equals("5v5 Ranked Solo"))
                    return entry;
            }
        } catch (Exception e) { }
        return null;
    }

    public static LeagueEntry getFlexEntry(String summonerId, LeagueShard shard) {
        try {
            for(int i = 0; i < 3; i++){
                LeagueEntry entry = riotApi.getLoLAPI().getLeagueAPI().getLeagueEntriesByPUUID(shard, summonerId).get(i);
                if(entry.getQueueType().commonName().equals("5v5 Ranked Flex Queue"))
                    return entry;
            }
        } catch (Exception e) { }
        return null;
    }

    public static LeagueEntry getRankEntry(Summoner s) {
        return getRankEntry(s.getPUUID(), s.getPlatform());
    }

    public static LeagueEntry getEntry(GameQueueType type, String puuid, LeagueShard shard) {
        if (type == GameQueueType.CHERRY) type = GameQueueType.RANKED_SOLO_5X5;
        LeagueEntry def = null;
        try {
            List<LeagueEntry> entries = riotApi.getLoLAPI().getLeagueAPI().getLeagueEntriesByPUUID(shard, puuid);
            for (LeagueEntry entry : entries) {
                if (entry.getQueueType().equals(type)) return entry;
                if (entry.getQueueType() == GameQueueType.RANKED_SOLO_5X5) def = entry;
            }
        } catch (Exception e) { }
        return def;
    }

    public static String getRankIcon(LeagueEntry entry) {

        return entry != null ? CustomEmojiHandler.getFormattedEmoji(entry.getTier()) : CustomEmojiHandler.getFormattedEmoji("unranked");
    }

    public static String getMastery(Summoner s, int nChamp){
        DecimalFormat df = new DecimalFormat("#,##0", new DecimalFormatSymbols(Locale.US));
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

    public static HashMap<Integer, ChampionMastery> getMastery(Summoner s) {
        HashMap<Integer, ChampionMastery> masteries = new HashMap<>();
        for(ChampionMastery mastery : s.getChampionMasteries())
            masteries.put(mastery.getChampionId(), mastery);
        return masteries;
    }

    public static String getMasteryByChamp(Summoner s, int champId) {
        String masteryString = "";
        try {
            for(ChampionMastery mastery : s.getChampionMasteries()){
                if(mastery.getChampionId() == champId){
                    int level = mastery.getChampionLevel() >= 10 ? 10 : mastery.getChampionLevel();
                    masteryString += CustomEmojiHandler.getFormattedEmoji("mastery" + level) + " ";
                    masteryString +=  CustomEmojiHandler.getFormattedEmoji(riotApi.getDDragonAPI().getChampion(mastery.getChampionId()).getName())
                                    + " **[" + mastery.getChampionLevel()+ "]** ";
                    return masteryString;
                }
            }

        } catch (Exception e) { }
        return masteryString;
    }

    public static String getMasteryByPuuid(String puuid, LeagueShard shard, int champion) {
        ChampionMastery mastery = LeagueHandler.getRiotApi().getLoLAPI().getMasteryAPI().getChampionMastery(shard, puuid, champion);
        if (mastery == null) return "";
        int level = mastery.getChampionLevel() >= 10 ? 10 : mastery.getChampionLevel();
        return CustomEmojiHandler.getFormattedEmoji("mastery" + level) + " " + CustomEmojiHandler.getFormattedEmoji(riotApi.getDDragonAPI().getChampion(mastery.getChampionId()).getName()) + " **[" + mastery.getChampionLevel()+ "]** ";
    }

    public static String getActivity(Summoner s){
        try {
            for(SpectatorParticipant partecipant : s.getCurrentGame().getParticipants()){
                if(partecipant.getSummonerId().equals(s.getSummonerId())) {
                    String gameName = LeagueHandler.formatMatchName(s.getCurrentGame().getGameQueueConfig());
                    return "Playing a " + gameName + " as " + CustomEmojiHandler.getFormattedEmoji(riotApi.getDDragonAPI().getChampion(partecipant.getChampionId()).getName()) + " " + riotApi.getDDragonAPI().getChampion(partecipant.getChampionId()).getName();
                }
            }
        } catch (Exception e) {
            return "Not in a game";
        }
        return "Not in a game";
    }

//     ▄████████ ███    █▄  ███▄▄▄▄      ▄████████
//    ███    ███ ███    ███ ███▀▀▀██▄   ███    ███
//    ███    ███ ███    ███ ███   ███   ███    █▀
//   ▄███▄▄▄▄██▀ ███    ███ ███   ███  ▄███▄▄▄
//  ▀▀███▀▀▀▀▀   ███    ███ ███   ███ ▀▀███▀▀▀
//  ▀███████████ ███    ███ ███   ███   ███    █▄
//    ███    ███ ███    ███ ███   ███   ███    ███
//    ███    ███ ████████▀   ▀█   █▀    ██████████
//    ███    ███

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

    public static String convertRuneRootToId(String name) {
        name = name.toLowerCase();
        String id = "";
        switch (name) {
            case "precision":
                id = "8000";
            break;
            case "domination":
                id = "8100";
            break;
            case "sorcery":
                id = "8200";
            break;
            case "resolve":
                id = "8400";
            break;
            case "inspiration":
                id = "8300";
            break;
        }
        return id;
    }

//     ▄████████    ▄█    █▄       ▄████████    ▄████████ ████████▄
//    ███    ███   ███    ███     ███    ███   ███    ███ ███   ▀███
//    ███    █▀    ███    ███     ███    ███   ███    ███ ███    ███
//    ███         ▄███▄▄▄▄███▄▄   ███    ███  ▄███▄▄▄▄██▀ ███    ███
//  ▀███████████ ▀▀███▀▀▀▀███▀  ▀███████████ ▀▀███▀▀▀▀▀   ███    ███
//           ███   ███    ███     ███    ███ ▀███████████ ███    ███
//     ▄█    ███   ███    ███     ███    ███   ███    ███ ███   ▄███
//   ▄████████▀    ███    █▀      ███    █▀    ███    ███ ████████▀
//                                             ███    ███


    public static OptionData getLeagueShardOptions(boolean required) {
        List<Choice> choices = new ArrayList<>();
        for (int i = 0; i < LeagueShard.values().length; i++) {
            if (LeagueShard.values()[i] == LeagueShard.UNKNOWN) continue;
            choices.add(new Choice(LeagueShard.values()[i].getRealmValue().toUpperCase(), String.valueOf(i)));
        }

        return new OptionData(OptionType.STRING, "region", "Region you want to get the summoner from", required).addChoices(choices);
    }

    public static LeagueShard getShardFromOrdinal(int ordinal){
        return LeagueShard.values()[ordinal];
    }

    public static OptionData getLeagueShardOptions() {
        return getLeagueShardOptions(false);
    }

    public static String getShardFlag(LeagueShard shard) {
        return CustomEmojiHandler.getFormattedEmoji(shard.getRealmValue().toUpperCase() + "_server");
    }

    public static String getRegionCode(LeagueShard shard) {
        String code = shard.getRealmValue();
        switch (shard) {
            case NA1:
            case JP1:
            case BR1:
            case TR1:
            case SG2:
            case PH2:
            case TW2:
            case VN2:
            case TH2:
                code = shard.getValue();
            break;
            case KR:
            case RU:
                code = code + "1";
            default:
            break;
        }
        return code;
    }


//  ▀█████████▄     ▄████████    ▄████████  ▄█    █▄     ▄████████    ▄████████ ▄██   ▄
//    ███    ███   ███    ███   ███    ███ ███    ███   ███    ███   ███    ███ ███   ██▄
//    ███    ███   ███    ███   ███    ███ ███    ███   ███    █▀    ███    ███ ███▄▄▄███
//   ▄███▄▄▄██▀   ▄███▄▄▄▄██▀   ███    ███ ███    ███  ▄███▄▄▄      ▄███▄▄▄▄██▀ ▀▀▀▀▀▀███
//  ▀▀███▀▀▀██▄  ▀▀███▀▀▀▀▀   ▀███████████ ███    ███ ▀▀███▀▀▀     ▀▀███▀▀▀▀▀   ▄██   ███
//    ███    ██▄ ▀███████████   ███    ███ ███    ███   ███    █▄  ▀███████████ ███   ███
//    ███    ███   ███    ███   ███    ███ ███    ███   ███    ███   ███    ███ ███   ███
//  ▄█████████▀    ███    ███   ███    █▀   ▀██████▀    ██████████   ███    ███  ▀█████▀
//                 ███    ███                                        ███    ███



    public static String getBraveryBuildJSON(int level, String[] roles, String[] champions) {
        try {
            URL url = new URI("https://api2.ultimate-bravery.net/bo/api/ultimate-bravery/v1/classic/dataset").toURL();
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("Accept", "application/json");
            con.setDoOutput(true);
            String jsonInputString = "{\"map\": 11,\"level\": " + level + ",\"roles\": [" + String.join(",", roles) +"],\"language\": \"en\",\"champions\": [" + String.join(",", champions) + "]}";
            try (OutputStream os = con.getOutputStream()) {
                byte[] input = jsonInputString.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            String json = null;
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(con.getInputStream(), "utf-8"))) {
                StringBuilder response = new StringBuilder();
                String responseLine = null;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                json = response.toString();
            }
            return json;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

    }

    public static String getBraveryBuildJSON() {
        int lvl = 20;
        String[] roles = {"0", "1", "2", "3", "4"};
        String[] champs = riotApi.getDDragonAPI().getChampions().values().stream().map(champ -> String.valueOf(champ.getId())).toArray(String[]::new);
        return getBraveryBuildJSON(lvl, roles, champs);
    }

//   ▄████████    ▄█    █▄       ▄████████   ▄▄▄▄███▄▄▄▄      ▄███████▄  ▄█   ▄██████▄  ███▄▄▄▄
//  ███    ███   ███    ███     ███    ███ ▄██▀▀▀███▀▀▀██▄   ███    ███ ███  ███    ███ ███▀▀▀██▄
//  ███    █▀    ███    ███     ███    ███ ███   ███   ███   ███    ███ ███▌ ███    ███ ███   ███
//  ███         ▄███▄▄▄▄███▄▄   ███    ███ ███   ███   ███   ███    ███ ███▌ ███    ███ ███   ███
//  ███        ▀▀███▀▀▀▀███▀  ▀███████████ ███   ███   ███ ▀█████████▀  ███▌ ███    ███ ███   ███
//  ███    █▄    ███    ███     ███    ███ ███   ███   ███   ███        ███  ███    ███ ███   ███
//  ███    ███   ███    ███     ███    ███ ███   ███   ███   ███        ███  ███    ███ ███   ███
//  ████████▀    ███    █▀      ███    █▀   ▀█   ███   █▀   ▄████▀      █▀    ▀██████▀   ▀█   █▀
//

    private static void loadChampions(){
        champions = riotApi.getDDragonAPI().getChampions().values().stream().map(champ -> champ.getName()).toArray(String[]::new);
    }

    public static String[] getChampions(){
        return champions;
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

    public static StaticChampion getChampionByName(String name) {
        StaticChampion champ = null;
        for (StaticChampion c : riotApi.getDDragonAPI().getChampions().values()) {
            if (c.getName().equalsIgnoreCase(name)) {
                champ = c;
                break;
            }
        }
        return champ;

    }

    public static StaticChampion getChampionById(int id) {
        StaticChampion champ = null;
        for (StaticChampion c : riotApi.getDDragonAPI().getChampions().values()) {
            if (c.getId() == id) {
                champ = c;
                break;
            }
        }
        return champ;
    }

    public static Emoji getEmojiByChampion(int champId) {
        StaticChampion champion = riotApi.getDDragonAPI().getChampion(champId);
        long emojiId = Long.parseLong(CustomEmojiHandler.getEmojiId(champion.getName()));
        return Emoji.fromCustom(champion.getName(), emojiId, false);
    }

    public static String getFormattedEmojiByChampion(int champion) {
        if (champion == -1) return CustomEmojiHandler.getFormattedEmoji("0");
        return CustomEmojiHandler.getFormattedEmoji(riotApi.getDDragonAPI().getChampion(champion).getName());
    }

//   ▄████████    ▄████████  ▄████████    ▄█    █▄       ▄████████
//  ███    ███   ███    ███ ███    ███   ███    ███     ███    ███
//  ███    █▀    ███    ███ ███    █▀    ███    ███     ███    █▀
//  ███          ███    ███ ███         ▄███▄▄▄▄███▄▄  ▄███▄▄▄
//  ███        ▀███████████ ███        ▀▀███▀▀▀▀███▀  ▀▀███▀▀▀
//  ███    █▄    ███    ███ ███    █▄    ███    ███     ███    █▄
//  ███    ███   ███    ███ ███    ███   ███    ███     ███    ███
//  ████████▀    ███    █▀  ████████▀    ███    █▀      ██████████
//

    public static void clearCache(URLEndpoint endpoint, Map<String, Object> data) {
        DataCall.getCacheProvider().clear(endpoint, data);
    }

    public static void clearCache(URLEndpoint endpoint, Summoner summoner) {
        Map<String, Object> data = new LinkedHashMap<>();

        switch (endpoint) {
            case V4_SUMMONER_BY_PUUID:
                data.put("platform", summoner.getPlatform());
                data.put("puuid", summoner.getPUUID());
                break;
            case V1_SHARED_ACCOUNT_BY_PUUID:
                data.put("platform", summoner.getPlatform().toRegionShard());
                data.put("puuid", summoner.getPUUID());
                break;
            case V5_MATCHLIST:
                data.put("platform", summoner.getPlatform().toRegionShard());
                data.put("puuid", summoner.getPUUID());
                data.put("queue", "null");
                data.put("type", "null");
                data.put("start", "null");
                data.put("count", "null");
                data.put("startTime", "null");
                data.put("endTime", "null");
                break;
            case V5_SPECTATOR_CURRENT:
                data.put("platform", summoner.getPlatform());
                data.put("summoner", summoner.getPUUID());
                break;
            case V4_LEAGUE_ENTRY_BY_PUUID:
                data.put("platform", summoner.getPlatform());
                data.put("puuid", summoner.getPUUID());
                break;

            default:
                break;
        }

        DataCall.getCacheProvider().clear(endpoint, data);
    }

    public static void clearSummonerCache(Summoner summoner) {
        clearCache(URLEndpoint.V4_SUMMONER_BY_PUUID, summoner);
        clearCache(URLEndpoint.V1_SHARED_ACCOUNT_BY_PUUID, summoner);
        clearCache(URLEndpoint.V5_SPECTATOR_CURRENT, summoner);
    }

//     ▄████████    ▄███████▄  ▄█        ▄█      ███
//    ███    ███   ███    ███ ███       ███  ▀█████████▄
//    ███    █▀    ███    ███ ███       ███▌    ▀███▀▀██
//    ███          ███    ███ ███       ███▌     ███   ▀
//  ▀███████████ ▀█████████▀  ███       ███▌     ███
//           ███   ███        ███       ███      ███
//     ▄█    ███   ███        ███▌    ▄ ███      ███
//   ▄████████▀   ▄████▀      █████▄▄██ █▀      ▄████▀
//                            ▀

    public static long[] getCurrentSplitRange() {
        long[] range = new long[2];
        long now = System.currentTimeMillis();

        try {
            FileReader reader = new FileReader("rsc" + File.separator + "Testing" + File.separator + "lol_testing" + File.separator + "split.json");
            JSONParser parser = new JSONParser();
            JSONObject file = (JSONObject) parser.parse(reader);
            JSONArray seasons = (JSONArray) file.get("seasons");

            for (int seasonIndex = seasons.size() - 1; seasonIndex >= 0; seasonIndex--) {
                JSONObject current = (JSONObject) seasons.get(seasonIndex);
                JSONArray splits = (JSONArray) current.get("splits");

                for (int i = 0; i < splits.size(); i++) {
                    JSONObject split = (JSONObject) splits.get(i);
                    String start = split.get("start_date").toString();
                    String end = split.get("end_date").toString();

                    long startMillis = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(start).getTime();
                    long endMillis = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(end).getTime();

                    if (now >= startMillis && now <= endMillis) {
                        range[0] = startMillis;
                        range[1] = endMillis;
                        return range;
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

        return range;
    }

    public static long[] getPreviousSplitRange() {
        long[] range = new long[2];
        long now = System.currentTimeMillis();
    
        try {
            FileReader reader = new FileReader("rsc" + File.separator + "Testing" + File.separator + "lol_testing" + File.separator + "split.json");
            JSONParser parser = new JSONParser();
            JSONObject file = (JSONObject) parser.parse(reader);
            JSONArray seasons = (JSONArray) file.get("seasons");
    
            List<long[]> allSplits = new ArrayList<>();
    
            for (int seasonIndex = 0; seasonIndex < seasons.size(); seasonIndex++) {
                JSONObject current = (JSONObject) seasons.get(seasonIndex);
                JSONArray splits = (JSONArray) current.get("splits");
    
                for (int i = 0; i < splits.size(); i++) {
                    JSONObject split = (JSONObject) splits.get(i);
                    String start = split.get("start_date").toString();
                    String end = split.get("end_date").toString();
    
                    long startMillis = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(start).getTime();
                    long endMillis = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(end).getTime();
    
                    allSplits.add(new long[]{startMillis, endMillis});
                }
            }
    
            long[] currentSplit = null;
    
            for (long[] split : allSplits) {
                if (now >= split[0] && now <= split[1]) {
                    currentSplit = split;
                    break;
                }
            }
    
            if (currentSplit != null) {
                for (int i = allSplits.size() - 1; i >= 0; i--) {
                    if (allSplits.get(i)[1] < currentSplit[0]) {
                        range[0] = allSplits.get(i)[0];
                        range[1] = allSplits.get(i)[1];
                        return range;
                    }
                }
            }
    
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    
        return null; // Nessuno split precedente trovato
    }

    public static String getCurrentSplitFormatted() {
        long now = System.currentTimeMillis();

        try {
            FileReader reader = new FileReader("rsc" + File.separator + "Testing" + File.separator + "lol_testing" + File.separator + "split.json");
            JSONParser parser = new JSONParser();
            JSONObject file = (JSONObject) parser.parse(reader);
            JSONArray seasons = (JSONArray) file.get("seasons");

            for (int seasonIndex = seasons.size() - 1; seasonIndex >= 0; seasonIndex--) {
                System.out.println(seasonIndex);
                JSONObject current = (JSONObject) seasons.get(seasonIndex);
                JSONArray splits = (JSONArray) current.get("splits");

                for (int i = 0; i < splits.size(); i++) {
                    JSONObject split = (JSONObject) splits.get(i);
                    String start = split.get("start_date").toString();
                    String end = split.get("end_date").toString();

                    long startMillis = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(start).getTime();
                    long endMillis = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(end).getTime();

                    System.out.println(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(start).getTime() + "-" + now + "-" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(end).getTime());

                    if (now >= startMillis && now <= endMillis && split.get("is_current") != null) {
                        return "Season " + current.get("season").toString() + " split " + split.get("split").toString();
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            return "Error occurred: " + e.getMessage();
        }

        return "No current split found";
    }

    public static boolean isCurrentSplit(long time) {
        long[] range = getCurrentSplitRange();
        return time >= range[0] && time <= range[1];
    }

}
