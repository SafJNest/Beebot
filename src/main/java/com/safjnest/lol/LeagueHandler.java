package com.safjnest.lol;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.core.cache.managers.GuildCache;
import com.safjnest.core.cache.managers.UserCache;
import com.safjnest.lol.model.Augment;
import com.safjnest.lol.model.rune.PageRunes;
import com.safjnest.lol.model.rune.Rune;
import com.safjnest.lol.model.summoner.Mastery;
import com.safjnest.lol.model.summoner.Rank;
import com.safjnest.lol.model.summoner.Summoner;
import com.safjnest.lol.service.MasteryService;
import com.safjnest.lol.service.RankService;
import com.safjnest.lol.service.SummonerService;
import com.safjnest.lol.service.StaticDataService;
import com.safjnest.lol.utils.ChampionUtils;
import com.safjnest.lol.utils.GameQueueTypeUtils;
import com.safjnest.lol.utils.LeagueMessageUtils;
import com.safjnest.lol.utils.LeagueShardUtils;
import com.safjnest.lol.utils.PatchUtils;
import com.safjnest.lol.utils.TierDivisionUtils;
import com.safjnest.model.UserData;
import com.safjnest.model.customemoji.CustomEmojiHandler;
import com.safjnest.model.guild.GuildData;
import com.safjnest.nosql.MongoDB;
import com.safjnest.redis.RedisKey;
import com.safjnest.utils.HttpUtils;
import com.safjnest.utils.SafJNest;
import com.safjnest.utils.SettingsLoader;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import no.stelar7.api.r4j.basic.APICredentials;
import no.stelar7.api.r4j.basic.calling.DataCall;
import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.cache.impl.EmptyCacheProvider;
import no.stelar7.api.r4j.impl.R4J;
import no.stelar7.api.r4j.pojo.lol.league.LeagueEntry;
import no.stelar7.api.r4j.pojo.lol.spectator.SpectatorGameInfo;
import no.stelar7.api.r4j.pojo.lol.spectator.SpectatorParticipant;
import no.stelar7.api.r4j.pojo.shared.RiotAccount;


/**
 * This class is used to handle all the League of Legends related stuff
 *
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 */

 public class LeagueHandler {

    private static R4J riotApi;

    private static String patch;

    private static String runesURL;

    private static HashMap<String, PageRunes> runesHandler = new HashMap<String, PageRunes>();
    private static ArrayList<Augment> augments = new ArrayList<>();

    static {

        LeagueHandler.riotApi = new R4J(new APICredentials(SettingsLoader.getSettings().getJsonSettings().getRiot().getKey())); 
        DataCall.setCacheProvider(EmptyCacheProvider.INSTANCE);
        LeagueHandler.patch = PatchUtils.getPatch() + ".1";
        LeagueHandler.runesURL = "https://ddragon.leagueoflegends.com/cdn/" + LeagueHandler.patch + "/data/en_US/runesReforged.json";

        loadRunes();
        loadAugments();
    }

    public static HashMap<String, PageRunes> getRunesHandler() {
        return runesHandler;
    }

    public static boolean isMatchDBCached(String gameId) {
        return MongoDB.hasMatch(gameId);
    }

    public static ArrayList<Augment> getAugments() {
        return augments;
    }

    public static R4J getRiotApi(){
        return riotApi;
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

    public static String getSpellName(int id) {
        return StaticDataService.getSummonerSpell(LeagueMessageUtils.normalizeSpellId(id)).getName();
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
            String json = StaticDataService.getText(RedisKey.DDRAGON_RUNES, PatchUtils.getPatch(),
                () -> {
                    try {
                        return HttpUtils.readUrl(runesURL);
                    } catch (IOException exception) {
                        throw new IllegalStateException(exception);
                    }
                });
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

    private static void loadAugments() {
        try {
            FileReader reader = new FileReader("rsc" + File.separator + "testing" + File.separator + "lol_testing" + File.separator + "augments.json");
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
                augments.add(new Augment(
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


    public static String getFormattedSummonerName(Summoner s) {
        if (s.riotId() != null && !s.riotId().isBlank()) return s.riotId();
        String dbName = MongoDB.getSummonerNameById(s.puuid(), s.region());
        if (dbName != null) return dbName;
        RiotAccount account = SummonerService.getRiotAccount(s.puuid(), s.region());
        if (account == null) return "";
        return account.getName() + "#" + account.getTag();
    }

    public static String getFormattedSummonerName(no.stelar7.api.r4j.pojo.lol.summoner.Summoner s) {
        String dbName = MongoDB.getSummonerNameById(s.getPUUID(), s.getPlatform());
        if (dbName != null) return dbName;
        RiotAccount account = SummonerService.getRiotAccountFromSummoner(s);
        if (account == null) return "";
        return account.getName() + "#" + account.getTag();
    }

    public static Summoner getSummonerFromDB(String userId){
        return getSummonerByUserData(UserCache.getUser(userId));
    }

    public static Summoner getSummonerByUserData(UserData user){
        try {
            Map<String, Summoner> accounts = user.getRiotAccounts();
            if (accounts == null || accounts.isEmpty()) return null;

            return accounts.values().iterator().next();
        } catch (Exception e) {return null;}
    }

    public static int getNumberOfProfile(String userId){
        try {
            return UserCache.getUser(userId).getRiotAccounts().size();
        } catch (Exception e) { return 0; }
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
            tag = LeagueShardUtils.getRegionCode(guild.getLeagueShard(event.getChannel().getId()));
        } else {
            name = args.split("#", 2)[0];
            tag = args.split("#", 2)[1];
        }
        LeagueShard shard = guild.getLeagueShard(event.getChannel().getId());
        String puuid = SummonerService.getPuuidByRiotId(name, tag, shard);
        return puuid == null ? null : SummonerService.get(puuid, shard);
    }

    public static Summoner getSummonerByArgs(SlashCommandEvent event) {
        GuildData guild = event.isFromGuild() ? GuildCache.getGuildOrPut(event.getGuild().getId()) : null;


        User user = event.getOption("user") != null ? event.getOption("user").getAsUser() : event.getUser();

        Summoner s = null;

        if (event.getOption("summoner") == null && (s = getSummonerFromDB(user.getId())) != null) {
            return s;
        }

        if (event.getOption("summoner") == null) {
            return null;
        }

        LeagueShard guildShard = event.isFromGuild()  ? guild.getLeagueShard(event.getChannel().getId()) : LeagueShard.EUW1;
        LeagueShard shard = event.getOption("region") != null ? LeagueShard.valueOf(event.getOption("region").getAsString()) : guildShard;

        if (event.getOption("summoner") != null) {
            s = SummonerService.get(event.getOption("summoner").getAsString(), shard);
        }

        if (s != null) return s;

        String summoner = event.getOption("summoner").getAsString().replaceAll("[\\p{C}]", ""); //when you copy the name from riot chat it adds some weird characters
        String tag = summoner.contains("#") ? summoner.split("#", 2)[1] : LeagueShardUtils.getRegionCode(shard);
        String name = summoner.contains("#") ? summoner.split("#", 2)[0] : summoner;

        String puuid = SummonerService.getPuuidByRiotId(name, tag, shard);
        return puuid == null ? null : SummonerService.get(puuid, shard);
    }

    public static boolean updateSummonerMongo(Summoner summoner) {
        if (summoner == null) return false;
        return MongoDB.upsertSummoner(summoner, null);
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
        return "https://ddragon.leagueoflegends.com/cdn/"+patch+"/img/profileicon/"+s.icon()+".png";
    }

    public static String getSummonerProfilePic(int id){
        return "https://ddragon.leagueoflegends.com/cdn/"+patch+"/img/profileicon/"+id+".png";
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

    private static final String QUEUE_COMMON_SOLO = "5v5 Ranked Solo";
    private static final String QUEUE_COMMON_FLEX = "5v5 Ranked Flex Queue";

    public static String getSoloQStats(Summoner s) {
        Rank rank = RankService.getByQueue(s.puuid(), s.region(), GameQueueType.RANKED_SOLO_5X5);
        String stats = rank != null ? formatStatsByRank(rank) : "";
        return stats.isEmpty()
            ? CustomEmojiHandler.getFormattedEmoji("Unranked") + " Unranked"
            : stats;
    }

    public static String getFlexStats(Summoner s) {
        Rank rank = RankService.getByQueue(s.puuid(), s.region(), GameQueueType.RANKED_FLEX_SR);
        String stats = rank != null ? formatStatsByRank(rank) : "";
        return stats.isEmpty()
            ? CustomEmojiHandler.getFormattedEmoji("Unranked") + " Unranked"
            : stats;
    }

    private static String formatStatsByRank(Rank rank) {
        if (rank.tier() == no.stelar7.api.r4j.basic.constants.types.lol.TierDivisionType.UNRANKED) return "";
        int wins = rank.wins();
        int losses = rank.losses();
        int games = wins + losses;
        long wrPercent = games > 0 ? (long) Math.ceil((wins * 100.0) / games) : 0;
        return CustomEmojiHandler.getFormattedEmoji(rank.tier().getTier()) + " "
            + rank.tier().prettyName().toUpperCase() + " " + rank.lp() + " LP\n"
            + "`(" + wins + "W/" + losses + "L) - " + wrPercent + "% WR`";
    }

    public static LeagueEntry getRankEntry(String puuid, LeagueShard shard) {
        return RankService.getEntry(puuid, shard, QUEUE_COMMON_SOLO);
    }

    public static LeagueEntry getRankEntry(Summoner s) {
        return getRankEntry(s.puuid(), s.region());
    }

    public static LeagueEntry getFlexEntry(String puuid, LeagueShard shard) {
        return RankService.getEntry(puuid, shard, QUEUE_COMMON_FLEX);
    }

    public static LeagueEntry getEntry(GameQueueType type, String puuid, LeagueShard shard) {
        if (GameQueueTypeUtils.isCherry(type)) {
            type = GameQueueType.RANKED_SOLO_5X5;
        }
        LeagueEntry def = null;
        for (LeagueEntry entry : RankService.getEntries(puuid, shard)) {
            if (entry.getQueueType().equals(type)) {
                return entry;
            }
            if (entry.getQueueType() == GameQueueType.RANKED_SOLO_5X5) {
                def = entry;
            }
        }
        return def;
    }

    public static String getRankIcon(LeagueEntry entry) {

        return entry != null ? CustomEmojiHandler.getFormattedEmoji(entry.getTier()) : CustomEmojiHandler.getFormattedEmoji("unranked");
    }

    public static String getMastery(Summoner s, int nChamp) {
        List<Mastery> list = MasteryService.get(s.puuid(), s.region());
        if (nChamp < 1 || nChamp > list.size()) {
            return "";
        }
        Mastery mastery = list.get(nChamp - 1);
        DecimalFormat df = new DecimalFormat("#,##0", new DecimalFormatSymbols(Locale.US));
        try {
            int level = mastery.level() >= 10 ? 10 : mastery.level();
            return CustomEmojiHandler.getFormattedEmoji("mastery" + level) + " "
                + CustomEmojiHandler.getFormattedEmoji(StaticDataService.getChampion(mastery.championId()).getName())
                + " **[" + mastery.level() + "]** "
                + StaticDataService.getChampion(mastery.championId()).getName()
                + " " + df.format(mastery.points())
                + " points";
        } catch (Exception e) {
            return "";
        }
    }

    public static HashMap<Integer, Mastery> getMastery(Summoner s) {
        HashMap<Integer, Mastery> masteries = new HashMap<>();
        for (Mastery mastery : MasteryService.get(s.puuid(), s.region())) {
            masteries.put(mastery.championId(), mastery);
        }
        return masteries;
    }

    public static String getMasteryByChamp(Summoner s, int champId) {
        if (s == null) return "";
        for (Mastery mastery : MasteryService.get(s.puuid(), s.region())) {
            if (mastery.championId() == champId) {
                try {
                    return formatMasteryLine(mastery);
                } catch (Exception e) {
                    return "";
                }
            }
        }
        return "";
    }

    public static String getMasteryByPuuid(String puuid, LeagueShard shard, int champion) {
        for (Mastery mastery : MasteryService.get(puuid, shard)) {
            if (mastery.championId() == champion) {
                return formatMasteryLine(mastery);
            }
        }
        return "";
    }

    private static String formatMasteryLine(Mastery mastery) {
        int level = mastery.level() >= 10 ? 10 : mastery.level();
        return CustomEmojiHandler.getFormattedEmoji("mastery" + level) + " "
            + CustomEmojiHandler.getFormattedEmoji(StaticDataService.getChampion(mastery.championId()).getName())
            + " **[" + mastery.level() + "]** ";
    }

    public static String getActivity(Summoner s) {
        try {
            SpectatorGameInfo game = SummonerService.getSpectatorGame(s.puuid(), s.region());
            if (game == null) {
                return "Not in a game";
            }
            for (SpectatorParticipant participant : game.getParticipants()) {
                if (participant.getPuuid().equals(s.puuid())) {
                    String gameName = GameQueueTypeUtils.prettyName(game.getGameQueueConfig());
                    return "Playing a " + gameName + " as "
                        + CustomEmojiHandler.getFormattedEmoji(StaticDataService.getChampion(participant.getChampionId()).getName()) + " "
                        + StaticDataService.getChampion(participant.getChampionId()).getName();
                }
            }
        } catch (Exception e) {
            return "Not in a game";
        }
        return "Not in a game";
    }

    public static EmbedBuilder getActivity(EmbedBuilder eb, Summoner s) {
        try {
            SpectatorGameInfo game = SummonerService.getSpectatorGame(s.puuid(), s.region());
            if (game == null) {
                return eb.setFooter("Currently not in a game", LeagueHandler.getSummonerProfilePic(s));
            }
            for (SpectatorParticipant participant : game.getParticipants()) {
                if (participant.getPuuid().equals(s.puuid())) {
                    String gameName = GameQueueTypeUtils.prettyName(game.getGameQueueConfig());
                    return eb.setFooter("Playing a " + gameName,
                        CustomEmojiHandler.getRichEmoji(StaticDataService.getChampion(participant.getChampionId()).getName()).getImageUrl());
                }
            }
        } catch (Exception e) {
            return eb.setFooter("Currently not in a game", LeagueHandler.getSummonerProfilePic(s));
        }
        return eb.setFooter("Currently not in a game", LeagueHandler.getSummonerProfilePic(s));
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

    public static String getFatherRuneById(int son){
        return runesHandler.get(String.valueOf(son)).getName();
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
        String[] champs = ChampionUtils.getChampionsNames().stream().toArray(String[]::new);
        return getBraveryBuildJSON(lvl, roles, champs);
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

//     ▄████████    ▄███████▄  ▄█        ▄█      ███
//    ███    ███   ███    ███ ███       ███  ▀█████████▄
//    ███    █▀    ███    ███ ███       ███▌    ▀███▀▀██
//    ███          ███    ███ ███       ███▌     ███   ▀
//  ▀███████████ ▀█████████▀  ███       ███▌     ███
//           ███   ███        ███       ███      ███
//     ▄█    ███   ███        ███▌    ▄ ███      ███
//   ▄████████▀   ▄████▀      █████▄▄██ █▀      ▄████▀
//                            ▀


}
