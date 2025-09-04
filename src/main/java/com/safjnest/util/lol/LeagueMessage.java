package com.safjnest.util.lol;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONObject;

import java.awt.Color;
import java.sql.SQLException;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.core.Bot;
import com.safjnest.core.Chronos.ChronoTask;
import com.safjnest.model.customemoji.CustomEmojiHandler;
import com.safjnest.sql.QueryResult;
import com.safjnest.sql.QueryRecord;
import com.safjnest.sql.database.LeagueDB;
import com.safjnest.util.DateHandler;
import com.safjnest.util.SafJNest;
import com.safjnest.util.lol.model.MatchData;
import com.safjnest.util.lol.model.ParticipantData;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.separator.Separator.Spacing;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.interactions.InteractionHook;
import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.api.regions.RegionShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.basic.constants.types.lol.TeamType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierDivisionType;
import no.stelar7.api.r4j.impl.R4J;
import no.stelar7.api.r4j.impl.lol.builders.matchv5.match.MatchListBuilder;
import no.stelar7.api.r4j.pojo.lol.championmastery.ChampionMastery;
import no.stelar7.api.r4j.pojo.lol.league.LeagueEntry;
import no.stelar7.api.r4j.pojo.lol.match.v5.ChampionBan;
import no.stelar7.api.r4j.pojo.lol.match.v5.LOLMatch;
import no.stelar7.api.r4j.pojo.lol.match.v5.MatchParticipant;
import no.stelar7.api.r4j.pojo.lol.match.v5.MatchTeam;
import no.stelar7.api.r4j.pojo.lol.shared.BannedChampion;
import no.stelar7.api.r4j.pojo.lol.spectator.SpectatorParticipant;
import no.stelar7.api.r4j.pojo.lol.staticdata.champion.StaticChampion;
import no.stelar7.api.r4j.pojo.lol.summoner.Summoner;
import no.stelar7.api.r4j.pojo.shared.RiotAccount;

public class LeagueMessage {


    public static List<MessageTopLevelComponent> composeButtons(Summoner s, String user_id, String id) {
        Button left = Button.primary(id + "-left", " ").withEmoji(CustomEmojiHandler.getRichEmoji("leftarrow"));
        Button right = Button.primary(id + "-right", " ").withEmoji(CustomEmojiHandler.getRichEmoji("rightarrow"));
        Button refresh = Button.primary(id + "-refresh", " ").withEmoji(CustomEmojiHandler.getRichEmoji("refresh"));

        Button profile = Button.primary(id + "-lol", " ").withEmoji(CustomEmojiHandler.getRichEmoji("user"));
        Button opgg = Button.primary(id + "-match", " ").withEmoji(CustomEmojiHandler.getRichEmoji("list2"));
        Button livegame = Button.primary(id + "-rank", " ").withEmoji(CustomEmojiHandler.getRichEmoji("game"));
        Button champ = Button.primary(id + "-champion", " ").withEmoji(CustomEmojiHandler.getRichEmoji("graph"));

        switch (id) {
            case "lol":
                profile = profile.asDisabled().withStyle(ButtonStyle.SUCCESS);
                break;
            case "match":
                opgg = opgg.withStyle(ButtonStyle.SUCCESS);
                break;
            case "rank":
                livegame = livegame.asDisabled().withStyle(ButtonStyle.SUCCESS);
                break;
            case "champion":
                champ = champ.asDisabled().withStyle(ButtonStyle.SUCCESS);
                break;
        }

        RiotAccount account = LeagueHandler.getRiotAccountFromSummoner(s);
        Button center = Button.primary(id + "-center-" + s.getPUUID() + "#" + s.getPlatform().name(), account.getName());
        center = center.asDisabled();

        if (user_id != null && LeagueHandler.getNumberOfProfile(user_id) > 1) {
            return List.of(ActionRow.of(left, center, right), ActionRow.of(profile, opgg, livegame, champ, refresh));

        }

        return List.of(ActionRow.of(profile, opgg, livegame, champ), ActionRow.of(center, refresh));
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

    public static EmbedBuilder getSummonerEmbed(Summoner s) {
        long[] split = LeagueHandler.getCurrentSplitRange();
        return getSummonerEmbed(s, split[0], split[1], GameQueueType.TEAM_BUILDER_RANKED_SOLO);
    }


    public static EmbedBuilder getSummonerEmbed(Summoner s, long time_start, long time_end, GameQueueType queue) {
        int summonerId = LeagueHandler.updateSummonerDB(s);
        RiotAccount account = LeagueHandler.getRiotAccountFromSummoner(s);

        EmbedBuilder builder = new EmbedBuilder();
        builder.setAuthor(account.getName() + "#" + account.getTag(), null, LeagueHandler.getSummonerProfilePic(s));
        builder.setColor(Bot.getColor());
        builder.setThumbnail(LeagueHandler.getSummonerProfilePic(s));
        
        String userId = LeagueDB.getUserIdByLOLAccountId(s.getPUUID(), s.getPlatform());
        if(userId != null){
            QueryRecord data = LeagueDB.getSummonerData(userId, s.getPUUID());
            if (data.getAsBoolean("tracking")) builder.setFooter("LPs tracking enabled for the current summoner.");
            else builder.setFooter("LPs tracking disabled for the current summoner");
        }

        String description = "Summoner is level **" + s.getSummonerLevel() + "** on " + LeagueHandler.getShardFlag(s.getPlatform()) + s.getPlatform().getRealmValue() + " server.";
        builder.setDescription(description);

        builder.addField("Solo/duo", LeagueHandler.getSoloQStats(s), true);
        builder.addField("Flex", LeagueHandler.getFlexStats(s), true);

        ((ChronoTask) () -> {
            LeagueDB.updateSummonerMasteries(summonerId, s.getChampionMasteries());
            LeagueDB.updateSummonerEntries(summonerId, LeagueHandler.getRiotApi().getLoLAPI().getLeagueAPI().getLeagueEntriesByPUUID(s.getPlatform(), s.getPUUID()));
        }).queue();


        String masteryString = "";
        for(int i = 1; i < 4; i++)
            masteryString += LeagueHandler.getMastery(s, i) + "\n";

        builder.addField("Highest Masteries", masteryString, false);


        QueryResult advanceData = LeagueDB.getAdvancedLOLData(summonerId, time_start, time_end, queue);

        if (!advanceData.isEmpty()) {
            LinkedHashMap<LaneType, String> laneStats = new LinkedHashMap<>();
            for (String stats : advanceData.arrayColumn("lanes_played")) {
                String[] lanes = stats.split(",");
                for (String lane : lanes) {
                    lane = lane.trim();
                    LaneType laneType = LaneType.values()[Integer.parseInt(lane.split("-")[0])];
                    laneStats.merge(laneType, lane.split("-")[1] + "-" + lane.split("-")[2], (oldValue, newValue) -> {
                        String[] oldStats = oldValue.split("-");
                        String[] newStats = newValue.split("-");
                        int totalWins = Integer.parseInt(oldStats[0]) + Integer.parseInt(newStats[0]);
                        int totalLosses = Integer.parseInt(oldStats[1]) + Integer.parseInt(newStats[1]);
                        return totalWins + "-" + totalLosses;
                    });
                }
            }

            laneStats = laneStats.entrySet()
                .stream()
                .sorted((entry1, entry2) -> {
                    String[] stats1 = entry1.getValue().split("-");
                    String[] stats2 = entry2.getValue().split("-");
                    int totalGames1 = Integer.parseInt(stats1[0]) + Integer.parseInt(stats1[1]);
                    int totalGames2 = Integer.parseInt(stats2[0]) + Integer.parseInt(stats2[1]);
                    return Integer.compare(totalGames2, totalGames1);
                })
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    Map.Entry::getValue,
                    (e1, e2) -> e1,
                    LinkedHashMap::new
                ));

            String laneString = "";
            for (LaneType lane : laneStats.keySet()) {
                String wins = laneStats.get(lane).split("-")[0];
                String losses = laneStats.get(lane).split("-")[1];
                int games = Integer.valueOf(wins) + Integer.valueOf(losses);

                if (lane == LaneType.NONE) 
                    continue;

                String percent = String.format("%.2f", Double.parseDouble(wins) * 100 / (Double.parseDouble(wins) + Double.parseDouble(losses)));
                laneString += LeagueHandler.getLaneTypeEmoji(lane) + " " + LeagueHandler.getPrettyName(lane) + " " + games + " games\n`(" +  wins + "W/" + losses + "L) - " + percent +"% WR`\n";
            }

            if (queue == null) {
                QueryResult gameData = LeagueDB.getAllGamesForAccount(summonerId, time_start, time_end);
                LinkedHashMap<GameQueueType, String> gameTypeStats = new LinkedHashMap<>();
                for (QueryRecord row : gameData) {
                    GameQueueType type = GameQueueType.values()[row.getAsInt("game_type")];
                    boolean win = row.getAsBoolean("win");
    
                    String stats = gameTypeStats.getOrDefault(type, "0-0");
                    int wins = Integer.valueOf(stats.split("-")[0]);
                    int losses = Integer.valueOf(stats.split("-")[1]);
    
                    if (win) wins++;
                    else losses++;
    
                    gameTypeStats.put(type, wins + "-" + losses);
                }
    
                gameTypeStats = gameTypeStats.entrySet()
                    .stream()
                    .sorted((entry1, entry2) -> {
                        String[] stats1 = entry1.getValue().split("-");
                        String[] stats2 = entry2.getValue().split("-");
                        int wins1 = Integer.parseInt(stats1[0]);
                        int losses1 = Integer.parseInt(stats1[1]);
                        int totalGames1 = wins1 + losses1;
                
                        int wins2 = Integer.parseInt(stats2[0]);
                        int losses2 = Integer.parseInt(stats2[1]);
                        int totalGames2 = wins2 + losses2;
                
                        if (totalGames1 != totalGames2) {
                            return Integer.compare(totalGames2, totalGames1);
                        }
                
                        double winRate1 = (totalGames1 > 0) ? (double) wins1 / totalGames1 : 0;
                        double winRate2 = (totalGames2 > 0) ? (double) wins2 / totalGames2 : 0;
                        return Double.compare(winRate2, winRate1);
                    })
                    .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                    ));
    
                String gameString = "";
                int count = 0;
                int otherWins = 0;
                int otherLosses = 0;         
                for (GameQueueType game : gameTypeStats.keySet()) {
                    String wins = gameTypeStats.get(game).split("-")[0];
                    String losses = gameTypeStats.get(game).split("-")[1];
                    int games = Integer.valueOf(wins) + Integer.valueOf(losses);
                
                    String percent = String.format("%.2f", Double.parseDouble(wins) * 100 / (Double.parseDouble(wins) + Double.parseDouble(losses)));
                
                    if (count < 4) {
                        gameString += LeagueHandler.getMapEmoji(game) + " " + LeagueHandler.formatMatchName(game) + " " + games + " games\n`(" + wins + "W/" + losses + "L) - " + percent + "% WR`\n";
                    } else {
                        otherWins += Integer.valueOf(wins);
                        otherLosses += Integer.valueOf(losses);
                    }
                    count++;
                }
                
                if (otherWins > 0 || otherLosses > 0) {
                    int otherGames = otherWins + otherLosses;
                    String otherPercent = String.format("%.2f", (double) otherWins * 100 / otherGames);
                    gameString += CustomEmojiHandler.getFormattedEmoji("special_mode") + "Others " + otherGames + " games\n`(" + otherWins + "W/" + otherLosses + "L) - " + otherPercent + "% WR`\n";
                }
                builder.addField("Games", gameString, true);
                builder.addField("Roles", laneString , true);
            }
            else if (queue == GameQueueType.TEAM_BUILDER_RANKED_SOLO) {
                LeagueEntry entry = LeagueHandler.getRankEntry(s.getPUUID(), s.getPlatform());
                
                int totalGamesAnalized = advanceData.arrayColumn("games").stream().mapToInt(Integer::parseInt).sum();
                String totalGames = entry != null ? String.valueOf(entry.getWins() + entry.getLosses()) : "0 (placements dont count)";
                
                builder.addField("Games", "The bot has analyzed " + totalGamesAnalized +" games over the " + totalGames + " you have played this split.\n" + laneString , false);
            }
            else if (queue == GameQueueType.RANKED_FLEX_SR) {
                LeagueEntry entry = LeagueHandler.getFlexEntry(s.getSummonerId(), s.getPlatform());
                
                int totalGamesAnalized = advanceData.arrayColumn("games").stream().mapToInt(Integer::parseInt).sum();
                String totalGames = entry != null ? String.valueOf(entry.getWins() + entry.getLosses()) : "0 (placements dont count)";
                
                builder.addField("Games", "The bot has analyzed " + totalGamesAnalized +" games over the " + totalGames + " you have played this split.\n" + laneString , false);
            }
            else {                
                int totalGamesAnalized = advanceData.arrayColumn("games").stream().mapToInt(Integer::parseInt).sum();            
                builder.addField("Games", "The bot has analyzed " + totalGamesAnalized +" games.\n" + laneString , false);
            }

            HashMap<Integer, ChampionMastery> masteries = LeagueHandler.getMastery(s);
            String champStats = "";
            for (int i = 0; i < 6 && i < advanceData.size(); i++) {
                QueryRecord row = advanceData.get(i);
                ChampionMastery mastery = masteries.get(row.getAsInt("champion"));
                champStats += LeagueMessageUtils.formatAdvancedData(row, mastery);
            }
            builder.addField("Champions", champStats, false);
        }

        builder.addField("Activity", LeagueHandler.getActivity(s), false);

        return builder;
    }

    public static List<MessageTopLevelComponent> getSummonerButtons(Summoner s, String user_id) {
        long[] time = LeagueHandler.getCurrentSplitRange();
        return getSummonerButtons(s, user_id, time[0], time[1], GameQueueType.TEAM_BUILDER_RANKED_SOLO);
    }

    public static List<MessageTopLevelComponent> getSummonerButtons(Summoner s, String user_id, long start, long end, GameQueueType queue) {
        int index = 0;

        List<MessageTopLevelComponent> buttons = new ArrayList<>(composeButtons(s, user_id, "lol"));

        boolean hasTrackedGames = LeagueDB.hasSummonerData(LeagueHandler.updateSummonerDB(s));

        if (hasTrackedGames) {
            long[] time = LeagueHandler.getCurrentSplitRange();
            long[] previousTime = LeagueHandler.getPreviousSplitRange();

            Button allQueue = Button.secondary("lol-queue-all", "All Queue");
            Button soloQ = Button.secondary("lol-queue-" + GameQueueType.TEAM_BUILDER_RANKED_SOLO, "Solo/Duo");
            Button flex = Button.secondary("lol-queue-" + GameQueueType.RANKED_FLEX_SR, "Flex");
            Button draft = Button.secondary("lol-queue-" + GameQueueType.TEAM_BUILDER_DRAFT_UNRANKED_5X5, "Draft");
            Button curretModeButton = Button.secondary("lol-queue-" + GameQueueType.CHERRY, "Arena");

            if (queue == null) {
                allQueue = allQueue.withStyle(ButtonStyle.SUCCESS);
            }
            else {
                switch (queue) {
                    case TEAM_BUILDER_RANKED_SOLO:
                        soloQ = soloQ.withStyle(ButtonStyle.SUCCESS);
                        break;
                    case RANKED_FLEX_SR:
                        flex = flex.withStyle(ButtonStyle.SUCCESS);
                        break;
                    case TEAM_BUILDER_DRAFT_UNRANKED_5X5:
                        draft = draft.withStyle(ButtonStyle.SUCCESS);
                        break;
                    case CHERRY:
                    case ULTBOOK:
                    case SWIFTPLAY:
                        curretModeButton = curretModeButton.withStyle(ButtonStyle.SUCCESS);
                        break;
                    default:
                        break;
                }
            }
                
            Button allSeason = Button.secondary("lol-season-all", "General");
            Button currentSplit = Button.secondary("lol-season-current", "Current Split");
            Button previousSplit = Button.secondary("lol-season-previous", "Previous Split");

            if (start == 0) allSeason = allSeason.withStyle(ButtonStyle.SUCCESS);
            else if (start == time[0]) currentSplit = currentSplit.withStyle(ButtonStyle.SUCCESS);
            else if (start == previousTime[0] && end == previousTime[1]) previousSplit = previousSplit.withStyle(ButtonStyle.SUCCESS);

            buttons.add(index, ActionRow.of(allQueue, soloQ, flex, draft, curretModeButton));
            index++;
            buttons.add(index, ActionRow.of(allSeason, currentSplit, previousSplit));
            index++;
        }

        return buttons;
    }

//   ▄██████▄     ▄███████▄    ▄██████▄     ▄██████▄
//  ███    ███   ███    ███   ███    ███   ███    ███
//  ███    ███   ███    ███   ███    █▀    ███    █▀
//  ███    ███   ███    ███  ▄███         ▄███
//  ███    ███ ▀█████████▀  ▀▀███ ████▄  ▀▀███ ████▄
//  ███    ███   ███          ███    ███   ███    ███
//  ███    ███   ███          ███    ███   ███    ███
//   ▀██████▀   ▄████▀        ████████▀    ████████▀
//

    public static StringSelectMenu getOpggMenu(Summoner summoner) {
        return getOpggMenu(summoner, null, 0);
    }

    public static StringSelectMenu getOpggMenu(Summoner summoner, GameQueueType queue, int index) {
        List<String> gameIds = getMatchIds(summoner, queue, index);

        ArrayList<SelectOption> options = new ArrayList<>();
        for(int i = 0; i < 5 && i < gameIds.size(); i++){
            try {

                LOLMatch match = LeagueHandler.getRiotApi().getLoLAPI().getMatchAPI().getMatch(summoner.getPlatform().toRegionShard(), gameIds.get(i));
                if (match.getParticipants().size() == 0) continue;

                MatchParticipant me = null;
                for(MatchParticipant mp : match.getParticipants())
                    if(mp.getSummonerId().equals(summoner.getSummonerId()))
                        me = mp;

                Emoji icon = LeagueHandler.getEmojiByChampion(me.getChampionId());

                String label = match.getGameDurationAsDuration().toMinutes() + " minutes " + LeagueHandler.formatMatchName(match.getQueue());
                String description = "As " + me.getChampionName() + " (" + me.getKills() + "/" + me.getDeaths() + "/" + me.getAssists() + " " + me.getTotalMinionsKilled() + " CS)";

                options.add(SelectOption.of(label, summoner.getPlatform().name() + "_" + match.getGameId() + "#" + summoner.getPUUID()).withEmoji(icon).withDescription(description));
            } catch (Exception e) {
                continue;
            }
        }

        if (options.isEmpty()) return null;

        return StringSelectMenu.create("opgg-select")
                .setPlaceholder("Select a game")
                .setMaxValues(1)
                .addOptions(options)
                .build();
    }

    public static EmbedBuilder getOpggEmbedMatch(Summoner s, LOLMatch match) {
        MatchParticipant me = null;
        for(MatchParticipant mp : match.getParticipants())
            if(mp.getSummonerId().equals(s.getSummonerId()))
                me = mp;

        EmbedBuilder eb = new EmbedBuilder();
        eb.setAuthor(me.getRiotIdName() + "#" + me.getRiotIdTagline(), null, LeagueHandler.getSummonerProfilePic(s));
        eb.setColor(Bot.getColor());
        eb.setTitle(LeagueHandler.formatMatchName(match.getQueue()));
        eb.setDescription((me.didWin() ? "Win" : "Lose") + " as " + CustomEmojiHandler.getFormattedEmoji(me.getChampionName()) + " " + me.getChampionName() + " in " + match.getGameDurationAsDuration().toMinutes() + " minutes");

        HashMap<MatchParticipant, HashMap<String, String>> totalStats = new HashMap<>();
        HashMap<TeamType, HashMap<String, String>> teamStats = new HashMap<>();
        TeamType blue = TeamType.BLUE;
        TeamType red = TeamType.RED;

        teamStats.put(blue, new HashMap<>());
        teamStats.put(red, new HashMap<>());

        double totalKill = 0;
        double totalCreeps = 0;

        String killPartecipation = "";
        String csPerMin = "";
        String personalStatsTxt = "";

        HashMap<String, String> personalstats = new HashMap<>();

        String build = "";


        switch (match.getQueue()) {
            case CHERRY:
                HashMap<String, ArrayList<String>> prova = new HashMap<>();
                prova.put("teamscuttles", new ArrayList<>());
                prova.put("teamporos", new ArrayList<>());
                prova.put("teamkrugs", new ArrayList<>());
                prova.put("teamminions", new ArrayList<>());

                prova.put("teamsentinels", new ArrayList<>());
                prova.put("teamgromps", new ArrayList<>());
                prova.put("teamraptors", new ArrayList<>());
                prova.put("teamwolves", new ArrayList<>());

                HashMap<Integer, String> positions = new HashMap<>();



                for(MatchParticipant mt : match.getParticipants()){
                    String rank = LeagueHandler.getRankIcon(LeagueHandler.getRankEntry(mt.getPuuid(), match.getPlatform()));
                    String name = "**" + mt.getRiotIdName() + "#" + mt.getRiotIdTagline() + "**" + rank;
                    String score = "`" + mt.getKills() + "/" + mt.getDeaths() + "/" + mt.getAssists() + "`";

                    String team = "";
                    switch (mt.getPlayerSubteamId()) {
                        case 1:
                            team = "teamporos";
                        break;
                        case 2:
                            team = "teamminions";
                        break;
                        case 3:
                            team = "teamscuttles";
                        break;
                        case 4:
                            team = "teamkrugs";
                        break;
                        case 5:
                            team = "teamraptors";
                        break;
                        case 6:
                            team = "teamsentinels";
                        break;
                        case 7:
                            team = "teamwolves";
                        break;
                        case 8:
                            team = "teamgromps";
                        break;
                    }
                    prova.get(team).add(CustomEmojiHandler.getFormattedEmoji(mt.getChampionName()) + name + "\n" + score);
                    positions.put(mt.getPlacement(), team);

                    totalStats.put(mt, new HashMap<>());
                    totalStats.get(mt).put("damageDealt", String.valueOf(mt.getTotalDamageDealtToChampions()));
                    totalStats.get(mt).put("damageTaken", String.valueOf(mt.getTotalDamageTaken()));

                    TeamType currentTeam = me.getPlayerSubteamId() == mt.getPlayerSubteamId() ? TeamType.BLUE : TeamType.RED;
                    teamStats.get(currentTeam).put("kills", String.valueOf(Integer.valueOf(teamStats.get(currentTeam).getOrDefault("kills", "0")) + mt.getKills()));
                    teamStats.get(currentTeam).put("damageDealt", String.valueOf(Integer.valueOf(teamStats.get(currentTeam).getOrDefault("damageDealt", "0")) + mt.getTotalDamageDealtToChampions()));
                    teamStats.get(currentTeam).put("damageTaken", String.valueOf(Integer.valueOf(teamStats.get(currentTeam).getOrDefault("damageTaken", "0")) + mt.getTotalDamageTaken()));

                }

                for (int j = 1; j <= 8; j++) {
                    String team = positions.get(j);
                    String field = CustomEmojiHandler.getFormattedEmoji(team) + " " + j + "th positon";
                    String value = prova.get(team).get(0) + "\n" + prova.get(team).get(1);
                    eb.addField(field, value, true);
                }

                totalKill = Double.valueOf(teamStats.get(me.getTeam()).get("kills")) == 0 ? 1 : Double.valueOf(teamStats.get(me.getTeam()).get("kills"));
                killPartecipation = String.format("%.1f", (Double.valueOf(me.getKills()) + Double.valueOf(me.getAssists())) / totalKill * 100);
                csPerMin = String.format("%.1f", totalCreeps / Double.valueOf(match.getGameDurationAsDuration().toMinutes()));
                personalstats = totalStats.get(me);
                personalStatsTxt = "**KDA**: " + me.getKills() + "/" + me.getDeaths() + "/" + me.getAssists() + " (" +  killPartecipation + "% kill partecipation)\n"
                                        + "**Damage Dealt to champion**: " + LeagueMessageUtils.formatNumber(personalstats.get("damageDealt")) + " (" + LeagueMessageUtils.getPosition(totalStats, personalstats, "damageDealt") + "th in the game)\n"
                                        + "**Damage Taken**: " + LeagueMessageUtils.formatNumber(personalstats.get("damageTaken")) + " (" + LeagueMessageUtils.getPosition(totalStats, personalstats, "damageTaken") + "th in the game)\n";

                eb.addField("Personal Stats", personalStatsTxt, false);

                build = CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getSummoner1Id()) + "_") + CustomEmojiHandler.getFormattedEmoji("a" + String.valueOf(me.getPlayerAugment1())) + " " + CustomEmojiHandler.getFormattedEmoji("a" + String.valueOf(me.getPlayerAugment2())) + "\n"
                        + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getSummoner2Id()) + "_") + CustomEmojiHandler.getFormattedEmoji("a" + String.valueOf(me.getPlayerAugment3())) + " " + CustomEmojiHandler.getFormattedEmoji("a" + String.valueOf(me.getPlayerAugment4())) + "\n"
                        + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem0())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem1())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem2())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem3())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem4())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem5())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem6()));
                eb.addField("Build", build, false);
                break;
            default:
                String blueSide = "";
                String redSide = "";

                String lpLabel = "";
                QueryResult result = LeagueDB.getSummonerData(LeagueDB.addLOLAccount(s));
                for (int j = 0; j < result.size(); j ++) {
                    QueryRecord row = result.get(j);
                    QueryRecord previosRow = j > 0 ? result.get(j - 1) : null;

                    if (row.getAsLong("game_id") != match.getGameId()) continue;

                    TierDivisionType rank = TierDivisionType.values()[row.getAsInt("rank")];
                    TierDivisionType prevRank = previosRow != null ? TierDivisionType.values()[row.getAsInt("rank")] : null;

                    String displayRank = LeagueMessageUtils.getFormatedRank(rank, true);

                    String gain = row.getAsInt("gain") > 0 ? "+" + row.getAsInt("gain") + " LP" : row.getAsInt("gain") + "";
                    if (prevRank != null) {
                        lpLabel = LeagueMessageUtils.getFormatedRank(prevRank, true) + " " + previosRow.getAsInt("lp") + "LP to " + displayRank + " " + row.getAsInt("lp") + "LP (" + gain + ")";
                    }


                    if (rank == TierDivisionType.UNRANKED) {
                        lpLabel += "(Placement)";
                    }
                    else if (j > 0 && row.getAsInt("rank") < result.get(j - 1).getAsInt("rank")) {
                        lpLabel = "Promoted to " + displayRank + " " + row.getAsInt("lp") + "LP";
                    }
                    else if (j > 0 && row.getAsInt("rank") > result.get(j - 1).getAsInt("rank")) {
                        lpLabel = "Demoted to " + displayRank + " " + row.getAsInt("lp") + "LP";
                    }
                    else if (!row.getAsBoolean("win") && row.getAsInt("gain") == 0) {
                        lpLabel += "-0 LP"; //demotion shield
                    }
                }

                for (MatchTeam team : match.getTeams()) {
                    if (team.getTeamId() != TeamType.BLUE && team.getTeamId() != TeamType.RED) continue;

                    String banText = teamStats.get(team.getTeamId()).getOrDefault("ban", "**Bans**\n");
                    for (ChampionBan ban : team.getBans()) {
                        if (ban.getChampionId() == -1) banText += CustomEmojiHandler.getFormattedEmoji("0") + " ";
                        else banText += CustomEmojiHandler.getFormattedEmoji(LeagueHandler.getRiotApi().getDDragonAPI().getChampion(ban.getChampionId()).getName()) + " ";

                    }
                    teamStats.get(team.getTeamId()).put("bans", banText);
                }


                for (MatchParticipant partecipant : match.getParticipants()) {
                    int kills = partecipant.getKills();
                    int tower = partecipant.getTurretKills();
                    int gold = partecipant.getGoldEarned();

                    int totalKills = Integer.valueOf(teamStats.get(partecipant.getTeam()).getOrDefault("kills", "0")) + kills;
                    int totalTowers = Integer.valueOf(teamStats.get(partecipant.getTeam()).getOrDefault("towers", "0")) + tower;
                    int totalGold = Integer.valueOf(teamStats.get(partecipant.getTeam()).getOrDefault("gold", "0")) + gold;

                    teamStats.get(partecipant.getTeam()).put("kills", String.valueOf(totalKills));
                    teamStats.get(partecipant.getTeam()).put("towers", String.valueOf(totalTowers));
                    teamStats.get(partecipant.getTeam()).put("gold", String.valueOf(totalGold));

                    String championText = teamStats.get(partecipant.getTeam()).getOrDefault("champions", "**Picks**\n");

                    String rank = LeagueHandler.getRankIcon(LeagueHandler.getRankEntry(partecipant.getPuuid(), match.getPlatform()));
                    String name = CustomEmojiHandler.getFormattedEmoji(partecipant.getChampionName()) + " **" + partecipant.getRiotIdName() + "#" + partecipant.getRiotIdTagline() + "**";
                    String kda = partecipant.getKills() + "/" + partecipant.getDeaths() + "/" + partecipant.getAssists() + "(" + (partecipant.getTotalMinionsKilled() + partecipant.getNeutralMinionsKilled()) + " CS)";

                    championText += name + rank + "\n`" + kda + "`\n";
                    teamStats.get(partecipant.getTeam()).put("champions", championText);

                    HashMap<String, String> stats = new HashMap<>();
                    stats.put("damageDealt", String.valueOf(partecipant.getTotalDamageDealtToChampions()));
                    stats.put("damageTaken", String.valueOf(partecipant.getTotalDamageTaken()));
                    stats.put("heal", String.valueOf(partecipant.getTotalHeal()));
                    stats.put("vision", String.valueOf(partecipant.getVisionScore()));

                    totalStats.put(partecipant, stats);

                }

                eb.setDescription((me.didWin() ? "Win" : "Lose") + " as " + CustomEmojiHandler.getFormattedEmoji(me.getChampionName()) + " " + me.getChampionName() + " in " + match.getGameDurationAsDuration().toMinutes() + " minutes\n" + lpLabel);

                String killsIcon = CustomEmojiHandler.getFormattedEmoji("kda");
                String goldIcon = CustomEmojiHandler.getFormattedEmoji("golds2");
                String towericon = CustomEmojiHandler.getFormattedEmoji("tower");

                blueSide += killsIcon + teamStats.get(blue).get("kills") + " ∙ " + towericon + teamStats.get(blue).get("towers") + " ∙ " + goldIcon + " " + LeagueMessageUtils.formatNumber(teamStats.get(blue).get("gold")) + "\n" + teamStats.get(blue).get("bans") + "\n\n" + teamStats.get(blue).get("champions");
                redSide += killsIcon + teamStats.get(red).get("kills") + " ∙ " + towericon + teamStats.get(red).get("towers") + " ∙ " + goldIcon + " " + LeagueMessageUtils.formatNumber(teamStats.get(red).get("gold")) + "\n" + teamStats.get(red).get("bans") + "\n\n" + teamStats.get(red).get("champions");
                eb.addField("Blue Side", blueSide, true);
                eb.addField("Red Side", redSide, true);

                personalstats = totalStats.get(me);


                totalCreeps = me.getTotalMinionsKilled() + me.getNeutralMinionsKilled();
                totalKill = Double.valueOf(teamStats.get(me.getTeam()).get("kills")) == 0 ? 1 : Double.valueOf(teamStats.get(me.getTeam()).get("kills"));
                killPartecipation = String.format("%.1f", (Double.valueOf(me.getKills()) + Double.valueOf(me.getAssists())) / totalKill * 100);
                csPerMin = String.format("%.1f", totalCreeps / Double.valueOf(match.getGameDurationAsDuration().toMinutes()));

                personalStatsTxt = "**KDA**: " + me.getKills() + "/" + me.getDeaths() + "/" + me.getAssists() + " (" +  killPartecipation + "% kill partecipation)\n"
                                        + "**CS**: " + totalCreeps + " (" + csPerMin + " CS/min)\n"
                                        + "**Vision Score**: " + me.getVisionScore() + " (" + me.getWardsPlaced() + " wards placed)\n"
                                        + "**Damage Dealt to champion**: " + LeagueMessageUtils.formatNumber(personalstats.get("damageDealt")) + " (" + LeagueMessageUtils.getPosition(totalStats, personalstats, "damageDealt") + "th in the game)\n";

                eb.addField("Personal Stats", personalStatsTxt, false);

                build = CustomEmojiHandler.getFormattedEmoji( String.valueOf(me.getSummoner1Id()) + "_") + LeagueMessageUtils.getFormattedRunes(me, 0) + "\n"
                        + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getSummoner2Id()) + "_") + LeagueMessageUtils.getFormattedRunes(me, 1) + "\n"
                        + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem0())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem1())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem2())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem3())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem4())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem5())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem6()));
                eb.addField("Build", build, false);

                break;
        }
        return eb;
    }

    public static EmbedBuilder getOpggEmbed(Summoner s) {
        return getOpggEmbed(s, null, 0);
    }

    public static List<String> getMatchIds(Summoner s, GameQueueType queue, int index) {
        List<String> gameIds = new ArrayList<>();
        MatchListBuilder builder = queue != null ? s.getLeagueGames().withCount(100).withBeginIndex(index).withQueue(queue).withPlatform(s.getPlatform()) : s.getLeagueGames().withCount(100).withBeginIndex(index).withPlatform(s.getPlatform());

        for (String gameId : builder.get()) {
            if (gameId.split("_")[0].equalsIgnoreCase(s.getPlatform().toString())) {
                gameIds.add(gameId);
            }
        }

        if (gameIds.size() > 5) return gameIds;

        for (String gameId : builder.get()) 
            if (!gameIds.contains(gameId)) gameIds.add(gameId);

        return gameIds;
    }

    private static EmbedBuilder getOpggEmbedMatch(EmbedBuilder eb, LOLMatch match, Summoner s, QueryResult result) {
        MatchParticipant me = null;
        for(MatchParticipant mp : match.getParticipants()){
            if(mp.getPuuid().equals(s.getPUUID())){
                me = mp;
            }
        }
        ArrayList<String> blue = new ArrayList<>();
        ArrayList<String> red = new ArrayList<>();
        for(MatchParticipant searchMe : match.getParticipants()){
            String partecipantString = CustomEmojiHandler.getFormattedEmoji(searchMe.getChampionName())
                                        + " "
                                        + searchMe.getKills() + "/" + searchMe.getDeaths() + "/" + searchMe.getAssists();

            if(searchMe.getTeam() == TeamType.BLUE)
                blue.add(partecipantString);
            else
                red.add(partecipantString);
        }

        String kda = me.getKills() + "/" + me.getDeaths()+ "/" + me.getAssists();
        String content = "";
        Instant instant = Instant.ofEpochMilli(match.getGameCreation() + match.getGameDurationAsDuration().toMillis() + 3600000*2);
        ZoneOffset offset = ZoneOffset.UTC;
        OffsetDateTime offsetDateTime = instant.atOffset(offset);
        String date = DateHandler.formatDate(offsetDateTime);
        date = "<t:" + ((match.getGameCreation()/1000) + match.getGameDurationAsDuration().getSeconds()) + ":R>";
        switch (match.getQueue()){
            case STRAWBERRY:
            content = CustomEmojiHandler.getFormattedEmoji(me.getChampionName()) + " Level: " +  me.getChampionLevel() + " | " + CustomEmojiHandler.getFormattedEmoji("golds") + me.getGoldEarned() +  "\n"
            + date  + " | ** " + LeagueMessageUtils.getFormattedDuration((match.getGameDuration())) + "**\n"
            + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem0())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem1())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem2())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem3())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem4())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem5())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem6()));
            eb.addField(
                "Swarm" + ": " + (me.didWin() ? "WIN" : "LOSE") , content, true);

            String swarmTeam = "";
            for(MatchParticipant mt : match.getParticipants())
                swarmTeam += CustomEmojiHandler.getFormattedEmoji(mt.getChampionName()) + " Level: " +  mt.getChampionLevel() + " | " + CustomEmojiHandler.getFormattedEmoji("golds") + mt.getGoldEarned() +  "\n";

            eb.addField("Swarm Team", swarmTeam, true);
            eb.addBlankField(true);
            break;

            case CHERRY:

                content = CustomEmojiHandler.getFormattedEmoji(me.getChampionName()) + kda +"\n"
                + date + " | **"+ LeagueMessageUtils.getFormattedDuration((match.getGameDuration()))  + "**\n"
                + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getSummoner1Id()) + "_") + CustomEmojiHandler.getFormattedEmoji("a" + String.valueOf(me.getPlayerAugment1())) + " " + CustomEmojiHandler.getFormattedEmoji("a" + String.valueOf(me.getPlayerAugment2())) + "\n"
                + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getSummoner2Id()) + "_") + CustomEmojiHandler.getFormattedEmoji("a" + String.valueOf(me.getPlayerAugment3())) + " " + CustomEmojiHandler.getFormattedEmoji("a" + String.valueOf(me.getPlayerAugment4())) + "\n"
                + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem0())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem1())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem2())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem3())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem4())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem5()));

                eb.addField(
                    "ARENA: " + (me.didWin() ? "WIN" : "LOSE") , content, true);

                HashMap<String, ArrayList<String>> prova = new HashMap<>();
                prova.put("teamscuttles", new ArrayList<>());
                prova.put("teamporos", new ArrayList<>());
                prova.put("teamkrugs", new ArrayList<>());
                prova.put("teamminions", new ArrayList<>());

                prova.put("teamsentinels", new ArrayList<>());
                prova.put("teamgromps", new ArrayList<>());
                prova.put("teamraptors", new ArrayList<>());
                prova.put("teamwolves", new ArrayList<>());

                HashMap<Integer, String> positions = new HashMap<>();

                for(MatchParticipant mt : match.getParticipants()){
                    String name = "";
                    String team = "";
                    switch (mt.getPlayerSubteamId()) {
                        case 1:
                            team = "teamporos";
                        break;
                        case 2:
                            team = "teamminions";
                        break;
                        case 3:
                            team = "teamscuttles";
                        break;
                        case 4:
                            team = "teamkrugs";
                        break;
                        case 5:
                            team = "teamraptors";
                        break;
                        case 6:
                            team = "teamsentinels";
                        break;
                        case 7:
                            team = "teamwolves";
                        break;
                        case 8:
                            team = "teamgromps";
                        break;
                    }
                    prova.get(team).add(CustomEmojiHandler.getFormattedEmoji(mt.getChampionName()) + name);
                    positions.put(mt.getPlacement(), team);
                }
                String blueTeam = "";
                String redTeam = "";
                for (int j = 1; j <= 8; j++) {
                    String team = positions.get(j);
                    String space = j % 2 == 0 ? "\n\n" : "\n";
                    if (j <= 4)
                        blueTeam += CustomEmojiHandler.getFormattedEmoji(team) + prova.get(team).get(0) + prova.get(team).get(1) + space;
                    else
                        redTeam += CustomEmojiHandler.getFormattedEmoji(team) + prova.get(team).get(0) + prova.get(team).get(1) + space;
                }
                eb.addField("Top 4", blueTeam, true);
                eb.addField("Others", redTeam, true);
            break;

            default:
            String matchTitle = LeagueHandler.formatMatchName(match.getQueue()) + ": " + (me.didWin() ? "WIN" : "LOSE");
            for (int j = 0; j < result.size(); j ++) {
                QueryRecord row = result.get(j);
                if (row.getAsLong("game_id") != match.getGameId()) continue;

                TierDivisionType rank = TierDivisionType.values()[row.getAsInt("rank")];

                String displayRank = LeagueMessageUtils.getFormatedRank(rank, true);

                String gain = row.getAsInt("gain") > 0 ? "+" + row.getAsInt("gain") + " LP" : row.getAsInt("gain") + " LP";


                if (rank == TierDivisionType.UNRANKED) {
                    matchTitle += "(Placement)";
                }
                else if (j > 0 && row.getAsInt("rank") < result.get(j - 1).getAsInt("rank")) {
                    matchTitle = "Promoted to " + displayRank + " " + row.getAsInt("lp") + "LP";
                }
                else if (j > 0 && row.getAsInt("rank") > result.get(j - 1).getAsInt("rank")) {
                    matchTitle = "Demoted to " + displayRank + " " + row.getAsInt("lp") + "LP";
                }
                else if (!row.getAsBoolean("win") && row.getAsInt("gain") == 0) {
                    matchTitle += "-0 LP"; //demotion shield
                }
                else {
                    matchTitle += " " + gain;
                }
            }
            content = CustomEmojiHandler.getFormattedEmoji(me.getChampionName()) + kda + " | " + "**Vision: **"+ me.getVisionScore()+"\n"
                        + date  + " | ** " + LeagueMessageUtils.getFormattedDuration((match.getGameDuration())) + "**\n"
                        + CustomEmojiHandler.getFormattedEmoji( String.valueOf(me.getSummoner1Id()) + "_") + LeagueMessageUtils.getFormattedRunes(me, 0) + "\n"
                        + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getSummoner2Id()) + "_") + LeagueMessageUtils.getFormattedRunes(me, 1) + "\n"
                        + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem0())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem1())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem2())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem3())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem4())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem5())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem6()));
                        eb.addField(
                            matchTitle, content, true);
                        String blueS = "";
                        String redS = "";
                        for(int j = 0; j < 5; j++)
                            blueS += blue.get(j) + "\n";
                        for(int j = 0; j < 5; j++)
                            redS += red.get(j) + "\n";
                        eb.addField("Blue Side", blueS, true);
                        eb.addField("Red Side", redS, true);
            break;


        }
        return eb;
    }

    public static EmbedBuilder getOpggEmbed(Summoner s, GameQueueType queue, int index) {
        LeagueShard shard = s.getPlatform();
        RegionShard region = shard.toRegionShard();

        RiotAccount account = LeagueHandler.getRiotAccountFromSummoner(s);
        EmbedBuilder eb = new EmbedBuilder();
        R4J r4j = LeagueHandler.getRiotApi();

        eb.setAuthor(account.getName() + "#" + account.getTag(), null, LeagueHandler.getSummonerProfilePic(s));
        eb.setColor(Bot.getColor());
        eb.setTitle("Showing matches from " + LeagueHandler.getShardFlag(shard) + " " + shard.getRealmValue());

        List<String> gameIds = getMatchIds(s, queue, index);

        QueryResult result = LeagueDB.getSummonerData(LeagueDB.addLOLAccount(s));

        for(int i = 0; i < 5 && i < gameIds.size(); i++){
            try {

                LOLMatch match = r4j.getLoLAPI().getMatchAPI().getMatch(region, gameIds.get(i));
                if (MatchTracker.isRemake(match))
                    continue;
                MatchTracker.queueMatch(match);
                if (match.getParticipants().size() == 0)
                    continue; //riot di merda che quando crasha il game lascia dati sporchi

                LeagueHandler.updateSummonerDB(match);
                eb = getOpggEmbedMatch(eb, match, s, result);
            } catch (Exception e) {
                e.printStackTrace();
                continue;
            }
        }

        if (eb.getFields().size() == 0)
            eb.setDescription("No games found");

        return eb;
    }


    @SuppressWarnings("unused")
    @Deprecated
    public static List<Container> getOpggEmbedV2(Summoner s, GameQueueType queue, int index) {
        List<Container> containers = new ArrayList<>();
        LeagueShard shard = s.getPlatform();
        RegionShard region = shard.toRegionShard();

        RiotAccount account = LeagueHandler.getRiotAccountFromSummoner(s);
        EmbedBuilder eb = new EmbedBuilder();
        MatchParticipant me = null;
        LOLMatch match = null;
        R4J r4j = LeagueHandler.getRiotApi();

        List<String> gameIds = getMatchIds(s, queue, index);

        QueryResult result = LeagueDB.getSummonerData(LeagueDB.addLOLAccount(s));

        for(int i = 0; i < 5 && i < gameIds.size(); i++){
            try {

                match = r4j.getLoLAPI().getMatchAPI().getMatch(region, gameIds.get(i));
                if (MatchTracker.isRemake(match))
                    continue;
                MatchTracker.queueMatch(match);
                if (match.getParticipants().size() == 0)
                    continue; //riot di merda che quando crasha il game lascia dati sporchi

                LeagueHandler.updateSummonerDB(match);
                for(MatchParticipant mp : match.getParticipants()){
                    if(mp.getPuuid().equals(s.getPUUID())){
                        me = mp;
                    }
                }
                ArrayList<String> blue = new ArrayList<>();
                ArrayList<String> red = new ArrayList<>();
                for(MatchParticipant searchMe : match.getParticipants()){
                    String partecipantString = CustomEmojiHandler.getFormattedEmoji(searchMe.getChampionName())
                                                + " "
                                                + searchMe.getKills() + "/" + searchMe.getDeaths() + "/" + searchMe.getAssists();

                    if(searchMe.getTeam() == TeamType.BLUE)
                        blue.add(partecipantString);
                    else
                        red.add(partecipantString);
                }

                String kda = me.getKills() + "/" + me.getDeaths()+ "/" + me.getAssists();
                String content = "";
                Instant instant = Instant.ofEpochMilli(match.getGameCreation() + match.getGameDurationAsDuration().toMillis() + 3600000*2);
                ZoneOffset offset = ZoneOffset.UTC;
                OffsetDateTime offsetDateTime = instant.atOffset(offset);
                String date = DateHandler.formatDate(offsetDateTime);
                date = "<t:" + ((match.getGameCreation()/1000) + match.getGameDurationAsDuration().getSeconds()) + ":R>";
                switch (match.getQueue()){
                    case STRAWBERRY:
                    content = CustomEmojiHandler.getFormattedEmoji(me.getChampionName()) + " Level: " +  me.getChampionLevel() + " | " + CustomEmojiHandler.getFormattedEmoji("golds") + me.getGoldEarned() +  "\n"
                    + date  + " | ** " + LeagueMessageUtils.getFormattedDuration((match.getGameDuration())) + "**\n"
                    + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem0())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem1())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem2())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem3())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem4())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem5())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem6()));

                    String swarmTeam = "";
                    for(MatchParticipant mt : match.getParticipants())
                        swarmTeam += CustomEmojiHandler.getFormattedEmoji(mt.getChampionName()) + " Level: " +  mt.getChampionLevel() + " | " + CustomEmojiHandler.getFormattedEmoji("golds") + mt.getGoldEarned() +  "\n";

                    break;

                    case CHERRY:

                        content = CustomEmojiHandler.getFormattedEmoji(me.getChampionName()) + kda +"\n"
                        + date + " | **"+ LeagueMessageUtils.getFormattedDuration((match.getGameDuration()))  + "**\n"
                        + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getSummoner1Id()) + "_") + CustomEmojiHandler.getFormattedEmoji("a" + String.valueOf(me.getPlayerAugment1())) + " " + CustomEmojiHandler.getFormattedEmoji("a" + String.valueOf(me.getPlayerAugment2())) + "\n"
                        + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getSummoner2Id()) + "_") + CustomEmojiHandler.getFormattedEmoji("a" + String.valueOf(me.getPlayerAugment3())) + " " + CustomEmojiHandler.getFormattedEmoji("a" + String.valueOf(me.getPlayerAugment4())) + "\n"
                        + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem0())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem1())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem2())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem3())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem4())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem5()));

                    break;

                    default:
                    String matchTitle = LeagueHandler.formatMatchName(match.getQueue()) + ": " + (me.didWin() ? "WIN" : "LOSE");
                    for (int j = 0; j < result.size(); j ++) {
                        QueryRecord row = result.get(j);
                        if (row.getAsLong("game_id") != match.getGameId()) continue;

                        TierDivisionType rank = TierDivisionType.values()[row.getAsInt("rank")];

                        String displayRank = LeagueMessageUtils.getFormatedRank(rank, true);

                        String gain = row.getAsInt("gain") > 0 ? "+" + row.getAsInt("gain") + " LP" : row.getAsInt("gain") + " LP";


                        if (rank == TierDivisionType.UNRANKED) {
                            matchTitle += "(Placement)";
                        }
                        else if (j > 0 && row.getAsInt("rank") < result.get(j - 1).getAsInt("rank")) {
                            matchTitle = "Promoted to " + displayRank + " " + row.getAsInt("lp") + "LP";
                        }
                        else if (j > 0 && row.getAsInt("rank") > result.get(j - 1).getAsInt("rank")) {
                            matchTitle = "Demoted to " + displayRank + " " + row.getAsInt("lp") + "LP";
                        }
                        else if (!row.getAsBoolean("win") && row.getAsInt("gain") == 0) {
                            matchTitle += "-0 LP"; //demotion shield
                        }
                        else {
                            matchTitle += " " + gain;
                        }
                    }
                    content = CustomEmojiHandler.getFormattedEmoji(me.getChampionName()) + kda + " | " + "**Vision: **"+ me.getVisionScore()+"\n"
                                + date  + " | ** " + LeagueMessageUtils.getFormattedDuration((match.getGameDuration())) + "**\n"
                                + CustomEmojiHandler.getFormattedEmoji( String.valueOf(me.getSummoner1Id()) + "_") + LeagueMessageUtils.getFormattedRunes(me, 0) + "\n"
                                + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getSummoner2Id()) + "_") + LeagueMessageUtils.getFormattedRunes(me, 1) + "\n"
                                + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem0())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem1())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem2())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem3())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem4())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem5())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem6()));

                    break;


                }

                StringSelectMenu menu = getSelectedMatchMenu(match);
                Button viewMatch = Button.primary("match-view-" + match.getGameId(), "View");

                Section section = Section.of(
                    viewMatch,
                    TextDisplay.of(content)
                );


                
                List<ContainerChildComponent> buttons = new ArrayList<>();
                buttons.add(section);
                buttons.add(Separator.createDivider(Spacing.SMALL));
                buttons.add(ActionRow.of(menu));
                
                containers.add(Container.of(buttons).withAccentColor(me.didWin() ? Color.green : Color.RED));

            } catch (Exception e) {
                e.printStackTrace();
                continue;
            }
        }
        return containers;
    }

    public static List<MessageTopLevelComponent> getOpggButtons(Summoner s, String user_id, GameQueueType queue, int index) {
        int order = 0;
        Button left = Button.primary("match-matchleft", " ").withEmoji(CustomEmojiHandler.getRichEmoji("leftarrow"));
        if (index == 0) left = left.asDisabled();

        Button page = Button.primary("match-index-" + index, "Match " + ((index/5)+1)).asDisabled();
        Button right = Button.primary("match-matchright", " ").withEmoji(CustomEmojiHandler.getRichEmoji("rightarrow"));

        List<MessageTopLevelComponent> buttons = new ArrayList<>(composeButtons(s, user_id, "match"));

        StringSelectMenu menu = LeagueMessage.getOpggMenu(s, queue, index);
        if (menu != null) {
            buttons.add(0, ActionRow.of(menu));
            order++;
        }

        buttons.add(order, LeagueMessageUtils.getOpggQueueTypeButtons(queue));
        order++;
        buttons.add(order, ActionRow.of(left, page, right));

        return buttons;
    }

    public static StringSelectMenu getSelectedMatchMenu(LOLMatch match) {
        ArrayList<SelectOption> options = new ArrayList<>();
        for(MatchParticipant p : match.getParticipants()){
            Emoji icon = LeagueHandler.getEmojiByChampion(p.getChampionId());
            options.add(SelectOption.of(p.getRiotIdName() + "#" + p.getRiotIdTagline(), p.getPuuid() + "#" + match.getPlatform().name()).withEmoji(icon));
        }

        return StringSelectMenu.create("rank-select"  + "#" + match.getGameId())
                .setPlaceholder("Select a summoner")
                .setMaxValues(1)
                .addOptions(options)
                .build();
    }

//   ▄█        ▄█   ▄█    █▄     ▄████████
//  ███       ███  ███    ███   ███    ███
//  ███       ███▌ ███    ███   ███    █▀
//  ███       ███▌ ███    ███  ▄███▄▄▄
//  ███       ███▌ ███    ███ ▀▀███▀▀▀
//  ███       ███  ███    ███   ███    █▄
//  ███▌    ▄ ███  ███    ███   ███    ███
//  █████▄▄██ █▀    ▀██████▀    ██████████
//  ▀

    public static EmbedBuilder getLivegameEmbed(Summoner summoner, List<SpectatorParticipant> spectators) {
        RiotAccount account = LeagueHandler.getRiotAccountFromSummoner(summoner);
        try {
            EmbedBuilder builder = new EmbedBuilder();
            builder.setAuthor(account.getName() + "#" + account.getTag(), null, LeagueHandler.getSummonerProfilePic(summoner));
            builder.setDescription("Currently playing a **" + LeagueHandler.formatMatchName(summoner.getCurrentGame().getGameQueueConfig()) + "** started <t:" + ((summoner.getCurrentGame().getGameStart()/1000)) + ":R>");
            builder.setColor(Bot.getColor());
            builder.setThumbnail(LeagueHandler.getSummonerProfilePic(summoner));

            switch (summoner.getCurrentGame().getGameQueueConfig()) {
                case CHERRY:
                    String field1 = "";
                    String field2 = "";
                    int i = 0;

                    for (SpectatorParticipant partecipant : spectators) {
                        Summoner s = LeagueHandler.getSummonerByPuuid(partecipant.getPuuid(), summoner.getPlatform());
                        String mastery = LeagueHandler.getMasteryByChamp(s, partecipant.getChampionId());
                        String stats = LeagueHandler.getRankIcon(LeagueHandler.getRankEntry(s));
                        String sum = " **" + partecipant.getRiotId() + "**";

                        if (i < 8) field1 += mastery + " " + sum + " " + stats + "\n";
                        else if (i < 16) field2 += mastery + " " + sum + " " + stats + "\n";
                        i++;
                    }
                    builder.addField("1 - 8 Players", field1, false);
                    builder.addField("8 - 16 Players", field2, false);
                    break;

                default:
                    String blueSide = "", redSide = "";
                    String blueBans = "", redBans = "";
                    String entryName = "";

                    for (BannedChampion bc : summoner.getCurrentGame().getBannedChampions()) {
                        String bcIcon = LeagueHandler.getFormattedEmojiByChampion(bc.getChampionId());

                        if (bc.getTeamId() == TeamType.BLUE.getValue()) blueBans += bcIcon + " ";
                        else redBans += bcIcon + " ";
                    }

                    for (SpectatorParticipant partecipant : spectators) {
                        String championIcon = LeagueHandler.getFormattedEmojiByChampion(partecipant.getChampionId());

                        String stats = CustomEmojiHandler.getFormattedEmoji("unranked") + "\n`Unranked`";
                        LeagueEntry entry = LeagueHandler.getEntry(summoner.getCurrentGame().getGameQueueConfig(), partecipant.getPuuid(), summoner.getPlatform());
                        if (entry != null) {
                            stats = CustomEmojiHandler.getFormattedEmoji(entry.getTier()) + "\n`" + LeagueMessageUtils.getFormatedRank(entry.getTierDivisionType(), false) + " " + String.valueOf(entry.getLeaguePoints()) + "LP " + Math.ceil((Double.valueOf(entry.getWins())/Double.valueOf(entry.getWins()+entry.getLosses()))*100)+"% WR`";
                            entryName = LeagueHandler.formatMatchName(entry.getQueueType());
                        }

                        String field = championIcon + "**" + partecipant.getRiotId() + "**" + stats + "\n";

                        if (partecipant.getTeam() == TeamType.BLUE) blueSide += field;
                        else redSide += field;

                    }

                    builder.addField("Rank queue", "Showing ranks about " + entryName, false);

                    builder.addField("**BLUE SIDE**", "**Bans\n**" + blueBans + "\n\n**Picks**\n" + blueSide, true);
                    builder.addField("**RED SIDE**", "**Bans\n**" + redBans + "\n\n**Picks**\n" + redSide, true);
                    break;
            }

            LeagueHandler.updateSummonerDB(summoner.getCurrentGame());


            builder.setFooter("For every gamemode would be use the SoloQ ranked data. Flex would be shown only if the game is a Flex game.");
            return builder;

        } catch (Exception e) {
            EmbedBuilder builder = new EmbedBuilder();
            builder.setTitle(account.getName() + "'s Game");
            builder.setColor(Bot.getColor());
            builder.setThumbnail(LeagueHandler.getSummonerProfilePic(summoner));
            builder.setDescription("This user is not in a game.");
            return builder;
        }
    }

    public static StringSelectMenu getLivegameMenu(Summoner summoner, List<SpectatorParticipant> spectators) {
        if (spectators == null || spectators.size() == 0) return null;

        ArrayList<SelectOption> options = new ArrayList<>();
        for(SpectatorParticipant p : spectators){
            Emoji icon = LeagueHandler.getEmojiByChampion(p.getChampionId());
            options.add(SelectOption.of(p.getRiotId(), p.getPuuid() + "#" + summoner.getPlatform().name()).withEmoji(icon));
        }

        return StringSelectMenu.create("rank-select")
                .setPlaceholder("Select a summoner")
                .setMaxValues(1)
                .addOptions(options)
                .build();
    }

    public static List<MessageTopLevelComponent> getLivegameButtons(Summoner s, String user_id) {
        return composeButtons(s, user_id, "rank");
    }

    public static void sendChampionMessage(InteractionHook hook, String userId, Summoner summoner, int summonerId, LeagueMessageParameter parameter) {
        MessageEmbed embed = buildEmbedChampion(userId, summoner, summonerId, parameter);
        List<MessageTopLevelComponent> components = getChampionButtons(userId, summoner, summonerId, parameter);
        hook.editOriginalEmbeds(embed).setComponents(components).queue();
    }

    public static void sendChampionMessage(CommandEvent event, String userId, Summoner summoner, int summonerId, LeagueMessageParameter parameter) {
        MessageEmbed embed = buildEmbedChampion(userId, summoner, summonerId, parameter);
        List<MessageTopLevelComponent> components = getChampionButtons(userId, summoner, summonerId, parameter);
        event.getChannel().sendMessageEmbeds(embed).addComponents(components).queue();
    }

    private static MessageEmbed buildEmbedChampion(String userId, Summoner summoner, int summonerId, LeagueMessageParameter parameter) {
        RiotAccount account = LeagueHandler.getRiotAccountFromSummoner(summoner);
        List<MatchData> matches = null;
        try {
            matches = LeagueDB.getMatchHistory(summonerId, parameter.getShowingChampion(), parameter.getPeriod()[0], parameter.getPeriod()[1], parameter.getQueueType(), parameter.getLaneType());
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        EmbedBuilder eb = new EmbedBuilder();

        if (parameter.isShowChampion()) eb.setThumbnail(LeagueHandler.getChampionProfilePic(parameter.getChampion().getName()));
        else eb.setThumbnail(LeagueHandler.getSummonerProfilePic(summoner));

        eb.setAuthor(account.getName() + "#" + account.getTag(), null, LeagueHandler.getSummonerProfilePic(summoner));
        eb.setColor(Bot.getColor());

        switch (parameter.getMessageType()) {
            case CHAMPION_OVERVIEW:
                eb = getGenericStats(eb, matches, summoner, summonerId, parameter);                
                break;
            case CHAMPION_MATCHUP:
                eb = getMatchups(eb, matches, summonerId, parameter);
                break;
            case CHAMPION_PING:
                eb = getPings(eb, matches, summonerId);
                break;
            case CHAMPION_OBJECTIVES:
                eb = getObjectives(eb, matches, summoner, summonerId);
                break;
            case CHAMPION_CHAMPIONS:
                eb = getAllChampions(eb, matches, summoner, summonerId, parameter);
                break;
            case CHAMPION_OPGG:
                eb = getChampionOPGG(eb, matches, summoner, parameter);
                break;
            default:
                break;
        }
        return eb.build();
    }


    private static EmbedBuilder getChampionOPGG(EmbedBuilder eb, List<MatchData> matches, Summoner s, LeagueMessageParameter parameter) {
        QueryResult result = LeagueDB.getSummonerData(LeagueDB.addLOLAccount(s));
        EmbedBuilder[] ebHolder = new EmbedBuilder[] { eb };

        matches.stream()
            .skip(parameter.getOffset())
            .limit(LeagueMessageType.CHAMPION_OPGG.getPageItem())
            .forEach(matchData -> {
                try {
                    LeagueShard lShard = LeagueShard.values()[matchData.leagueShard];
                    RegionShard shard = lShard.toRegionShard();
                    LOLMatch match = LeagueHandler.getRiotApi().getLoLAPI().getMatchAPI().getMatch(shard, lShard.getValue() + "_" + matchData.gameId);
                    if (MatchTracker.isRemake(match))
                        return;
                    if (match.getParticipants().size() == 0)
                        return;
                    ebHolder[0] = getOpggEmbedMatch(ebHolder[0], match, s, result);
                } catch (Exception e) {
                    // Optionally log or handle exception
                }
            });
        eb = ebHolder[0];
        int pages = (int) Math.ceil((double) matches.size() / 5);
        int currentPage = (parameter.getOffset() / 5) + 1;
        eb.setFooter("Page " + currentPage + " / " + pages);
        return eb;
    }

    private static EmbedBuilder getObjectives(EmbedBuilder eb, List<MatchData> matches, Summoner summoner, int summonerId) {        
        HashMap<String, Integer> monsterKills = new HashMap<>();
        HashMap<String, Integer> monsterParticipation = new HashMap<>();
        HashMap<String, Integer> totalMonstersPerType = new HashMap<>();
        
        HashMap<String, Integer> buildingKills = new HashMap<>();
        HashMap<String, Integer> buildingParticipation = new HashMap<>();
        HashMap<String, Integer> totalBuildingsPerType = new HashMap<>();
        
        int totalGames = 0;
        
        for (MatchData match : matches) {
            ParticipantData participant = match.participants.stream()
                    .filter(p -> p.summonerId == summonerId)
                    .findFirst()
                    .orElse(null);
            
            if (participant == null) continue;
            
            String puuid = participant.puuid;
            
            if (!match.events.has("participants") || (!match.events.has("monster_events") && !match.events.has("building_events"))) {
                continue;
            }
            
            JSONObject participantsMap = match.events.getJSONObject("participants");
            Integer participantId = null;
            
            for (String key : participantsMap.keySet()) {
                if (participantsMap.getString(key).equals(puuid)) {
                    participantId = Integer.parseInt(key);
                    break;
                }
            }
            
            if (participantId == null) continue;
            totalGames++;
            
            if (match.events.has("monster_events")) {
                JSONArray monsters = match.events.getJSONArray("monster_events");
                for (Object objEvent : monsters) {
                    JSONObject event = (JSONObject) objEvent;
                    String monster = event.getString("monster");
                    String subtype = event.optString("subtype", "");
                    
                    String monsterKey;
                    if (!subtype.isEmpty()) {
                        monsterKey = subtype.toLowerCase().replace("_", " ");
                    } else {
                        monsterKey = monster.toLowerCase().replace("_", " ");
                    }
                    
                    totalMonstersPerType.put(monsterKey, totalMonstersPerType.getOrDefault(monsterKey, 0) + 1);
                    
                    int killer = event.getInt("killer");
                    JSONArray assists = event.getJSONArray("assists");
                    
                    boolean playerKilled = (killer == participantId);
                    boolean playerAssisted = false;
                    
                    for (Object assistObj : assists) {
                        if (assistObj instanceof Integer && (Integer) assistObj == participantId) {
                            playerAssisted = true;
                            break;
                        }
                    }
                    
                    if (playerKilled) {
                        monsterKills.put(monsterKey, monsterKills.getOrDefault(monsterKey, 0) + 1);
                    }
                    
                    if (playerKilled || playerAssisted) {
                        monsterParticipation.put(monsterKey, monsterParticipation.getOrDefault(monsterKey, 0) + 1);
                    }
                }
            
                if (match.events.has("building_events")) {
                    JSONArray buildings = match.events.getJSONArray("building_events");
                    for (Object objEvent : buildings) {
                        JSONObject event = (JSONObject) objEvent;
                        String building = event.getString("building");
                        
                        String buildingKey = building.toLowerCase().replace("_", " ");
                        
                        totalBuildingsPerType.put(buildingKey, totalBuildingsPerType.getOrDefault(buildingKey, 0) + 1);
                        
                        int killer = event.getInt("killer");
                        JSONArray assists = event.getJSONArray("assists");
                        
                        boolean playerKilled = (killer == participantId);
                        boolean playerAssisted = false;
                        
                        for (Object assistObj : assists) {
                            if (assistObj instanceof Integer && (Integer) assistObj == participantId) {
                                playerAssisted = true;
                                break;
                            }
                        }
                        
                        if (playerKilled) {
                            buildingKills.put(buildingKey, buildingKills.getOrDefault(buildingKey, 0) + 1);
                        }
                        
                        if (playerKilled || playerAssisted) {
                            buildingParticipation.put(buildingKey, buildingParticipation.getOrDefault(buildingKey, 0) + 1);
                        }
                    }
                }
            }
        }
        
        if (!totalMonstersPerType.isEmpty()) {
            StringBuilder monsterStats = new StringBuilder();
            for (String monsterType : totalMonstersPerType.keySet()) {
                int total = totalMonstersPerType.get(monsterType);
                int kills = monsterKills.getOrDefault(monsterType, 0);
                int participation = monsterParticipation.getOrDefault(monsterType, 0);
                double avgPerGame = totalGames > 0 ? (double) total / totalGames : 0;
                
                if (participation > 0) {
                    String displayName = capitalizeFirstLetter(monsterType);
                    monsterStats.append(String.format("**%s**: %dK %dP (%.1f avg)\n", 
                        displayName, kills, participation, avgPerGame));
                }
            }
            
            if (monsterStats.length() > 0) {
                String statsText = monsterStats.toString();
                if (statsText.length() > 1000) {
                    statsText = statsText.substring(0, 997) + "...";
                }
                eb.addField("🐉 Monsters", statsText, false);
            }
        }
        
        if (!totalBuildingsPerType.isEmpty()) {
            StringBuilder buildingStats = new StringBuilder();
            for (String buildingType : totalBuildingsPerType.keySet()) {
                int total = totalBuildingsPerType.get(buildingType);
                int kills = buildingKills.getOrDefault(buildingType, 0);
                int participation = buildingParticipation.getOrDefault(buildingType, 0);
                double avgPerGame = totalGames > 0 ? (double) total / totalGames : 0;
                
                // Solo se ha partecipato almeno una volta
                if (participation > 0) {
                    String displayName = capitalizeFirstLetter(buildingType);
                    buildingStats.append(String.format("**%s**: %dK %dP (%.1f avg)\n", 
                        displayName, kills, participation, avgPerGame));
                }
            }
            
            if (buildingStats.length() > 0) {
                // Limita la lunghezza se troppo lunga
                String statsText = buildingStats.toString();
                if (statsText.length() > 1000) {
                    statsText = statsText.substring(0, 997) + "...";
                }
                eb.addField("🏰 Buildings", statsText, false);
            }
        }
        
        return eb;
    }

private static String capitalizeFirstLetter(String text) {
    if (text == null || text.isEmpty()) return text;
    
    String[] words = text.split(" ");
    StringBuilder result = new StringBuilder();
    
    for (String word : words) {
        if (!word.isEmpty()) {
            result.append(Character.toUpperCase(word.charAt(0)))
                  .append(word.substring(1).toLowerCase())
                  .append(" ");
        }
    }
    
    return result.toString().trim();
}
    private static List<MessageTopLevelComponent> getChampionButtons(String userId, Summoner summoner, int summonerId, LeagueMessageParameter parameter) {
        StaticChampion champion = parameter.getChampion();
        
        Button left = Button.primary("champion-left", " ").withEmoji(CustomEmojiHandler.getRichEmoji("leftarrow"));
        Button right = Button.primary("champion-right", " ").withEmoji(CustomEmojiHandler.getRichEmoji("rightarrow"));

        RiotAccount account = LeagueHandler.getRiotAccountFromSummoner(summoner);
        Button center = Button.primary("champion-center-" + summoner.getPUUID() + "#" + summoner.getPlatform().name(), account.getName());
        center = center.asDisabled();

        Button settings = Button.primary("champion-change-" + parameter.getChampionId(), " ").withEmoji(CustomEmojiHandler.getRichEmoji("shuffle"));


        Button championButton = Button.secondary("champion-champion-0", " ").withEmoji(CustomEmojiHandler.getRichEmoji("blank")).asDisabled();
        if (champion != null) {
            championButton = Button.secondary("champion-champion-" + champion.getId(), champion.getName()).withEmoji(CustomEmojiHandler.getRichEmoji(champion.getName()));
            championButton = parameter.isShowChampion() ? championButton.withStyle(ButtonStyle.SUCCESS) : championButton;
        }


        Button generic = Button.primary("champion-type-" + LeagueMessageType.CHAMPION_OVERVIEW, "Overview");
        Button matchups = Button.primary("champion-type-" + LeagueMessageType.CHAMPION_MATCHUP, "Matchups");
        Button pings = Button.primary("champion-type-" + LeagueMessageType.CHAMPION_PING, "Pings");
        Button objectives = Button.primary("champion-type-" + LeagueMessageType.CHAMPION_OBJECTIVES, "Objectives");
        Button champions = Button.primary("champion-type-" + LeagueMessageType.CHAMPION_CHAMPIONS, "Champions");
        Button opgg = Button.primary("champion-type-" + LeagueMessageType.CHAMPION_OPGG, "Opgg");


        switch (parameter.getMessageType()) {
            case CHAMPION_OVERVIEW:
                generic = generic.withStyle(ButtonStyle.SUCCESS).asDisabled();
                break;
            case CHAMPION_MATCHUP:
                matchups = matchups.withStyle(ButtonStyle.SUCCESS).asDisabled();
                break;
            case CHAMPION_PING:
                pings = pings.withStyle(ButtonStyle.SUCCESS).asDisabled();
                break;
            case CHAMPION_OBJECTIVES:
                objectives = objectives.withStyle(ButtonStyle.SUCCESS).asDisabled();
                break;
            case CHAMPION_CHAMPIONS:
                champions = champions.withStyle(ButtonStyle.SUCCESS).asDisabled();
            case CHAMPION_OPGG:
                opgg = opgg.withStyle(ButtonStyle.SUCCESS).asDisabled();
            default:
                break;
        }


        Button allSeason = Button.secondary("champion-season-all", "General");
        Button currentSplit = Button.secondary("champion-season-current", "Current Split");
        Button previousSplit = Button.secondary("champion-season-previous", "Previous Split");

        long[] time = LeagueHandler.getCurrentSplitRange();
        long[] previousTime = LeagueHandler.getPreviousSplitRange();

        if (parameter.getTimeStart() == 0) allSeason = allSeason.withStyle(ButtonStyle.SUCCESS);
        else if (parameter.getTimeStart() == time[0]) currentSplit = currentSplit.withStyle(ButtonStyle.SUCCESS);
        else if (parameter.getTimeStart() == previousTime[0] && parameter.getTimeEnd() == previousTime[1]) previousSplit = previousSplit.withStyle(ButtonStyle.SUCCESS);

        List<MessageTopLevelComponent> rows = new ArrayList<>();
        if (parameter.getQueueType() != GameQueueType.CHERRY) rows.add(LeagueMessageUtils.getLaneComponents("champion", parameter.getLaneType()));

        rows.add(ActionRow.of(allSeason, currentSplit, previousSplit));
        rows.add(LeagueMessageUtils.getOpggQueueTypeButtons("champion", ButtonStyle.SECONDARY, parameter.getQueueType()));
        rows.add(ActionRow.of(generic, opgg, champions, matchups, pings));


        if (parameter.getMessageType().hasPageButtons()) {
            Button leftPage = Button.secondary("champion-leftpage-" + parameter.getOffset(), "Previous Page");
            Button rightPage = Button.secondary("champion-rightpage-" + parameter.getOffset(), "Next Page");
            if (userId != null && LeagueHandler.getNumberOfProfile(userId) > 1)
                rows.add(ActionRow.of(left, center, right,leftPage, rightPage));
            else 
                rows.add(ActionRow.of(center, leftPage, rightPage));
            return rows;
        }

        if (userId != null && LeagueHandler.getNumberOfProfile(userId) > 1)
            rows.add(ActionRow.of(left, center, right, championButton, settings));
        else 
            rows.add(ActionRow.of(center, championButton, settings));

        return rows;
    }

    private static EmbedBuilder getPings(EmbedBuilder eb, List<MatchData> matches, int summonerId) {
        HashMap<String, Integer> pings = new HashMap<>();

        for (MatchData match : matches) {
            for (ParticipantData participant : match.participants) {
                if (participant.summonerId != summonerId) continue;
                for (String ping : participant.pings.keySet()) 
                    pings.put(ping, pings.getOrDefault(ping, 0) + participant.pings.get(ping));
            }
        }

        List<Map.Entry<String, Integer>> sortedPings = pings.entrySet()
            .stream()
            .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
            .collect(Collectors.toList());

        String pingString = "";
        for (Map.Entry<String, Integer> entry : sortedPings) {
            if (entry.getKey().equals("basic")) continue;

            String pingName = "";
            switch (entry.getKey()) {
                case "command":
                    pingName = "Generic Ping";
                    break;
                default:
                    pingName = entry.getKey();
                    pingName = Arrays.stream(pingName.replace("_", " ").split(" "))
                        .map(word -> word.isEmpty() ? "" : Character.toUpperCase(word.charAt(0)) + word.substring(1).toLowerCase())
                        .collect(Collectors.joining(" "));
                    break;
            }
            pingString += CustomEmojiHandler.getFormattedEmoji(entry.getKey() + "_ping") + " " + pingName + "\n`" +
                entry.getValue() + " total (" + String.format("%.2f", (double)entry.getValue() / matches.size()) + " avg)`\n";
        }

        eb.addField("Pings Usage", pingString, false);

        return eb;
    }

    private static EmbedBuilder getMatchups(EmbedBuilder eb, List<MatchData> matches, int summonerId, LeagueMessageParameter parameter) {
        HashMap<Integer, int[]> laneVsWinrate = new HashMap<>();
        HashMap<Integer, int[]> duoWinrate = new HashMap<>();

        for (MatchData match : matches) {
            for (ParticipantData participant : match.participants) {
                if (participant.summonerId != summonerId) {
                    continue;
                }

                LaneType lane = participant.lane;
                TeamType team = participant.team;

                boolean win = participant.win;



                List<Integer> enemyChamps = match.participants.stream()
                    .filter(p -> p.team != team)
                    .filter(p -> {
                        return p.lane == lane;
                    })
                    .map(p -> p.champion)
                    .collect(Collectors.toList());

                for (int c : enemyChamps) {
                    laneVsWinrate.computeIfAbsent(c, k -> new int[2]);
                    laneVsWinrate.get(c)[(win ? 0 : 1)]++;   
                }
                             

                if (parameter.isDuo()) {
                    List<Integer> allyChamps = match.participants.stream()
                        .filter(p -> p.team == team)
                        .filter(p -> p.id != participant.id)
                        .filter(p -> (parameter.getQueueType() == GameQueueType.CHERRY && p.subTeam == participant.subTeam) || p.lane == LaneType.BOT || p.lane == LaneType.UTILITY)
                        .map(p -> p.champion)
                        .collect(Collectors.toList());
                    
                    for (int c : allyChamps) {
                        duoWinrate.computeIfAbsent(c, k -> new int[2]);
                        duoWinrate.get(c)[(win ? 0 : 1)]++;   
                    }
                }
            }
        }

        eb = LeagueMessageUtils.buildMatchups("matchups", eb, laneVsWinrate);
        if (parameter.isDuo())
            eb = LeagueMessageUtils.buildMatchups("duo", eb, duoWinrate);

        return eb;
    }

    private static EmbedBuilder getGenericStats(EmbedBuilder eb, List<MatchData> matches, Summoner summoner, int summonerId, LeagueMessageParameter parameter) {

        if (matches.size() == 0) {
            eb.setDescription("Not enough games");
            return eb;
        }


        LinkedHashMap<LaneType, String> laneStats = new LinkedHashMap<>();
        LinkedHashMap<GameQueueType, String> queueStats = new LinkedHashMap<>();
        HashMap<String, Accumulator> overallStats = new HashMap<>();

        HashMap<Integer, int[]> laneVsWinrate = new HashMap<>();
        HashMap<Integer, int[]> duoWinrate = new HashMap<>();

        HashMap<String, Set<Integer>> unique = new HashMap<>();

        HashMap<Integer, ParticipantChampionStat> championStats = new HashMap<>();
        HashMap<Integer, ChampionMastery> masteries = LeagueHandler.getMastery(summoner);

        long timePlayed = 0;
        long oldest = Long.MAX_VALUE;
        long newest = Long.MIN_VALUE;

        unique.put("champion", new HashSet<>());
        unique.put("lane", new HashSet<>());
        unique.put("queue", new HashSet<>());
        for (MatchData match : matches) {
            timePlayed += match.getDuration();
            if (oldest > match.timeStart) oldest = match.timeStart;
            if (newest < match.timeStart) newest = match.timeStart;

            for (ParticipantData participant : match.participants) {
                if (participant.summonerId != summonerId) continue;

                unique.getOrDefault("champion", new HashSet<>()).add(participant.champion);
                unique.getOrDefault("lane", new HashSet<>()).add(participant.lane.ordinal());
                unique.getOrDefault("queue", new HashSet<>()).add(match.gameType.ordinal());

                LaneType lane = participant.lane;
                TeamType team = participant.team;
                GameQueueType gameQueue = match.gameType;
                boolean win = participant.win;
    
                String kda = participant.kda;
                int kills = Integer.parseInt(kda.split("/")[0]);
                int deaths = Integer.parseInt(kda.split("/")[1]);
                int assists = Integer.parseInt(kda.split("/")[2]);
        
                laneStats.merge(lane, (win ? "1-0" : "0-1"), (oldValue, newValue) -> {
                    String[] oldStats = oldValue.split("-");
                    String[] newStats = newValue.split("-");
                    int totalWins = Integer.parseInt(oldStats[0]) + Integer.parseInt(newStats[0]);
                    int totalLosses = Integer.parseInt(oldStats[1]) + Integer.parseInt(newStats[1]);
                    return totalWins + "-" + totalLosses;
                });
    
                queueStats.merge(gameQueue, (win ? "1-0" : "0-1"), (oldValue, newValue) -> {
                    String[] oldStats = oldValue.split("-");
                    String[] newStats = newValue.split("-");
                    int totalWins = Integer.parseInt(oldStats[0]) + Integer.parseInt(newStats[0]);
                    int totalLosses = Integer.parseInt(oldStats[1]) + Integer.parseInt(newStats[1]);
                    return totalWins + "-" + totalLosses;
                });

                double csPerMin = participant.cs / (match.getDuration() / 1000 / 60);
    
                overallStats.computeIfAbsent("damage", k -> new Accumulator()).add(participant.damage);
                overallStats.computeIfAbsent("damage_building", k -> new Accumulator()).add(participant.damageBuilding);
                overallStats.computeIfAbsent("cs", k -> new Accumulator()).add(participant.cs);
                overallStats.computeIfAbsent("vision_score", k -> new Accumulator()).add(participant.visionScore);
                overallStats.computeIfAbsent("ward", k -> new Accumulator()).add(participant.ward);
                overallStats.computeIfAbsent("ward_killed", k -> new Accumulator()).add(participant.wardKilled);
                overallStats.computeIfAbsent("gold_earned", k -> new Accumulator()).add(participant.goldEarned);
                overallStats.computeIfAbsent("kills", k -> new Accumulator()).add(kills);
                overallStats.computeIfAbsent("deaths", k -> new Accumulator()).add(deaths);
                overallStats.computeIfAbsent("assists", k -> new Accumulator()).add(assists);
                overallStats.computeIfAbsent("cs_min", k -> new Accumulator()).add((int) csPerMin);

                championStats.computeIfAbsent(participant.champion, p -> new ParticipantChampionStat(participant.champion)).add(kills, deaths, assists, participant.gain, participant.win);

                if (parameter.getQueueType() == GameQueueType.CHERRY) {
                    int placement = participant.subTeamPlacement;
                    if (placement == 1) overallStats.computeIfAbsent("arena_first", k -> new Accumulator()).add(1);
                    else if (placement == 2) overallStats.computeIfAbsent("arena_second", k -> new Accumulator()).add(1);
                    else if (placement == 3) overallStats.computeIfAbsent("arena_third", k -> new Accumulator()).add(1);
                    overallStats.computeIfAbsent("arena_placement", k -> new Accumulator()).add(placement);
                }

                int teamKills = match.participants.stream()
                    .filter(p -> p.team == team)
                    .mapToInt(p -> Integer.parseInt(p.kda.split("/")[0]))
                    .sum();
                
                double killParticipation = teamKills == 0 ? 0 : (double) (kills + assists) / teamKills;
                overallStats.computeIfAbsent("kill_participation", k -> new Accumulator()).add((int)(killParticipation * 100));

                boolean isDuo = lane == LaneType.BOT || lane == LaneType.UTILITY;

                List<Integer> enemyChamps = match.participants.stream()
                    .filter(p -> p.team != team)
                    .filter(p -> {
                        return p.lane == lane;
                    })
                    .map(p -> p.champion)
                    .collect(Collectors.toList());
                    
                for (int c : enemyChamps) {
                    laneVsWinrate.computeIfAbsent(c, k -> new int[2]);
                    laneVsWinrate.get(c)[(win ? 0 : 1)]++;   
                }
         

                if (isDuo) {
                    List<Integer> allyChamps = match.participants.stream()
                        .filter(p -> p.team == team)
                        .filter(p -> p.id != participant.id)
                        .filter(p -> p.lane == LaneType.BOT || p.lane == LaneType.UTILITY)
                        .map(p -> p.champion)
                        .collect(Collectors.toList());
                    
                    for (int c : allyChamps) {
                        duoWinrate.computeIfAbsent(c, k -> new int[2]);
                        duoWinrate.get(c)[(win ? 0 : 1)]++;   
                    }
                }
            }
        }

        laneStats = laneStats.entrySet()
            .stream()
            .sorted((entry1, entry2) -> {
                String[] stats1 = entry1.getValue().split("-");
                String[] stats2 = entry2.getValue().split("-");
                int totalGames1 = Integer.parseInt(stats1[0]) + Integer.parseInt(stats1[1]);
                int totalGames2 = Integer.parseInt(stats2[0]) + Integer.parseInt(stats2[1]);
                return Integer.compare(totalGames2, totalGames1);
            })
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (e1, e2) -> e1,
                LinkedHashMap::new
            ));

        queueStats = queueStats.entrySet()
            .stream()
            .sorted((entry1, entry2) -> {
                String[] stats1 = entry1.getValue().split("-");
                String[] stats2 = entry2.getValue().split("-");
                int totalGames1 = Integer.parseInt(stats1[0]) + Integer.parseInt(stats1[1]);
                int totalGames2 = Integer.parseInt(stats2[0]) + Integer.parseInt(stats2[1]);
                return Integer.compare(totalGames2, totalGames1);
            })
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (e1, e2) -> e1,
                LinkedHashMap::new
            ));

        String laneString = "";
        for (LaneType lane : laneStats.keySet()) {
            String wins = laneStats.get(lane).split("-")[0];
            String losses = laneStats.get(lane).split("-")[1];
            int games = Integer.valueOf(wins) + Integer.valueOf(losses);

            if (lane == LaneType.NONE) 
                continue;

            String percent = String.format("%.2f", Double.parseDouble(wins) * 100 / (Double.parseDouble(wins) + Double.parseDouble(losses)));
            laneString += LeagueHandler.getLaneTypeEmoji(lane) + " " + LeagueHandler.getPrettyName(lane) + " " + games + " games\n`(" +  wins + "W/" + losses + "L) - " + percent +"% WR`\n";
        }

        String gameString = "";
        int count = 0;
        int otherWins = 0;
        int otherLosses = 0;         
        for (GameQueueType game : queueStats.keySet()) {
            String wins = queueStats.get(game).split("-")[0];
            String losses = queueStats.get(game).split("-")[1];
            int games = Integer.valueOf(wins) + Integer.valueOf(losses);
        
            String percent = String.format("%.2f", Double.parseDouble(wins) * 100 / (Double.parseDouble(wins) + Double.parseDouble(losses)));
        
            if (count < 4) {
                gameString += LeagueHandler.getMapEmoji(game) + " " + LeagueHandler.formatMatchName(game) + " " + games + " games\n`(" + wins + "W/" + losses + "L) - " + percent + "% WR`\n";
            } else {
                otherWins += Integer.valueOf(wins);
                otherLosses += Integer.valueOf(losses);
            }
            count++;
        }
        
        if (otherWins > 0 || otherLosses > 0) {
            int otherGames = otherWins + otherLosses;
            String otherPercent = String.format("%.2f", (double) otherWins * 100 / otherGames);
            gameString += CustomEmojiHandler.getFormattedEmoji("special_mode") + "Others " + otherGames + " games\n`(" + otherWins + "W/" + otherLosses + "L) - " + otherPercent + "% WR`\n";
        }


        String kda = String.format("%.2f", overallStats.get("kills").avg()) + "/" + String.format("%.2f", overallStats.get("deaths").avg()) + "/" + String.format("%.2f", overallStats.get("assists").avg());
        String visionScore = String.format("%.2f", overallStats.get("vision_score").avg()) + " VS (" + 
                String.format("%.2f", overallStats.get("ward").avg()) + " placed / " +
                String.format("%.2f", overallStats.get("ward_killed").avg()) + " destroyed)";

        String cs =  String.format("%.2f", overallStats.get("cs").avg()) + " (" +
             String.format("%.2f", overallStats.get("cs_min").avg()) + " / min)";

        String damaString = String.format("%.2f", overallStats.get("damage").avg()) + " to champ / " +
            String.format("%.2f", overallStats.get("damage_building").avg()) + " to buildings";
    
        String arenaPlacement = "";
        if (parameter.getQueueType() == GameQueueType.CHERRY) {
            arenaPlacement = "1. " + overallStats.getOrDefault("arena_first", new Accumulator()).count + " times\n" +
                "2. " + overallStats.getOrDefault("arena_second", new Accumulator()).count + " times\n" +
                "3. " + overallStats.getOrDefault("arena_third", new Accumulator()).count + " times\n" +
                "avg. " + String.format("%.2f", overallStats.get("arena_placement").avg()) + " placement";
        }

        
        String performace = 
            (!arenaPlacement.equals("") ? "**Arena**\n`" + arenaPlacement + "`\n" : "") +
            "**KDA**\n`" + kda + " (" + String.format("%.2f", overallStats.get("kill_participation").avg()) + "% kp)`\n" +
            "**Vision Score**\n`" + visionScore + "`\n" +
            "**CS**\n`" + cs + "`\n" +
            "**Damage**\n`" + damaString + "`\n" +
            "**Gold Earned**\n`" + String.format("%.2f", overallStats.get("gold_earned").avg()) + "`\n";

        String championString = "";
        if (!parameter.isShowChampion()) 
            championString = " with " + unique.get("champion").size() + " different champions";
        else {
            int champId = (int) unique.get("champion").toArray()[0];
            championString = " with " + CustomEmojiHandler.getFormattedEmoji(LeagueHandler.getChampionById(champId).getName()) + " " + LeagueHandler.getChampionById(champId).getName();
        }

        eb.setDescription(
            "Summoner has played **" + matches.size() + "** games " + championString +
            "\nA total of **" +  SafJNest.getFormattedDurationWithUnits(timePlayed) + "**\n" +
            "Oldest game: <t:" + (oldest / 1000) + ":R>\n" +
            "Newest game: <t:" + (newest / 1000) + ":R>"
        );
        
        eb.addField("Games", gameString, true);

        if (parameter.getQueueType() != GameQueueType.CHERRY)
            eb.addField("Roles", laneString , true);

        if (!parameter.isShowChampion()) {
            String champStats = championStats.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue().getGames(), a.getValue().getGames()))
                .limit(6)
                .map(entry -> {
                    ParticipantChampionStat stat = entry.getValue();
                    ChampionMastery mastery = masteries.get(stat.getChampion());
                    return LeagueMessageUtils.formatAdvancedData(stat, mastery);
                })
                .collect(Collectors.joining("\n"));

            eb.addField("Champions", champStats, false);
        }

        eb.addField("Avarage Performace", performace, false);

        return eb;

    }

    private static EmbedBuilder getAllChampions(EmbedBuilder eb, List<MatchData> matches, Summoner summoner, int summonerId, LeagueMessageParameter parameter) {

        if (matches.size() == 0) {
            eb.setDescription("Not enough games");
            return eb;
        }

        HashMap<Integer, ParticipantChampionStat> championStats = new HashMap<>();
        HashMap<Integer, ChampionMastery> masteries = LeagueHandler.getMastery(summoner);

        HashMap<String, Set<Integer>> unique = new HashMap<>();

        unique.put("champion", new HashSet<>());


        for (MatchData match : matches) {
            for (ParticipantData participant : match.participants) {
                if (participant.summonerId != summonerId) continue;

                    unique.getOrDefault("champion", new HashSet<>()).add(participant.champion);
                    String kda = participant.kda;
                    int kills = Integer.parseInt(kda.split("/")[0]);
                    int deaths = Integer.parseInt(kda.split("/")[1]);
                    int assists = Integer.parseInt(kda.split("/")[2]);
                    championStats.computeIfAbsent(participant.champion, p -> new ParticipantChampionStat(participant.champion)).add(kills, deaths, assists, participant.gain, participant.win);
            }
        }
        String champStats = championStats.entrySet().stream()
            .sorted((a, b) -> Integer.compare(b.getValue().getGames(), a.getValue().getGames()))
            .skip(parameter.getOffset())
            .limit(10)
            .map(entry -> {
                ParticipantChampionStat stat = entry.getValue();
                ChampionMastery mastery = masteries.get(stat.getChampion());
                return LeagueMessageUtils.formatAdvancedData(stat, mastery);
            })
            .collect(Collectors.joining("\n"));

        
        int champs = unique.get("champion").size();
        eb.setDescription(
            "Summoner has played **" + matches.size() + "** games with " + champs + " different champions\n\n" +
            champStats
        );

        int pages = (int) Math.ceil((double) champs / 10);
        int currentPage = (parameter.getOffset() / 10) + 1;
        eb.setFooter("Page " + currentPage + " / " + pages);
        return eb;

    }


}
