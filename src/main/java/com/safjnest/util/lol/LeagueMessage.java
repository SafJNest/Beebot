package com.safjnest.util.lol;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


import java.text.DecimalFormat;

import com.safjnest.core.Bot;
import com.safjnest.model.customemoji.CustomEmojiHandler;
import com.safjnest.sql.DatabaseHandler;
import com.safjnest.sql.QueryCollection;
import com.safjnest.sql.QueryRecord;
import com.safjnest.util.DateHandler;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.LayoutComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.api.regions.RegionShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.basic.constants.types.lol.TeamType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierDivisionType;
import no.stelar7.api.r4j.impl.R4J;
import no.stelar7.api.r4j.impl.lol.builders.matchv5.match.MatchListBuilder;
import no.stelar7.api.r4j.pojo.lol.league.LeagueEntry;
import no.stelar7.api.r4j.pojo.lol.match.v5.ChampionBan;
import no.stelar7.api.r4j.pojo.lol.match.v5.LOLMatch;
import no.stelar7.api.r4j.pojo.lol.match.v5.MatchParticipant;
import no.stelar7.api.r4j.pojo.lol.match.v5.MatchTeam;
import no.stelar7.api.r4j.pojo.lol.match.v5.PerkSelection;
import no.stelar7.api.r4j.pojo.lol.match.v5.PerkStyle;
import no.stelar7.api.r4j.pojo.lol.shared.BannedChampion;
import no.stelar7.api.r4j.pojo.lol.spectator.SpectatorParticipant;
import no.stelar7.api.r4j.pojo.lol.staticdata.champion.StaticChampion;
import no.stelar7.api.r4j.pojo.lol.summoner.Summoner;
import no.stelar7.api.r4j.pojo.shared.RiotAccount;

public class LeagueMessage {


    public static List<LayoutComponent> composeButtons(Summoner s, String user_id, String id) {
        Button left = Button.primary(id + "-left", " ").withEmoji(CustomEmojiHandler.getRichEmoji("leftarrow"));
        Button right = Button.primary(id + "-right", " ").withEmoji(CustomEmojiHandler.getRichEmoji("rightarrow"));
        Button refresh = Button.primary(id + "-refresh", " ").withEmoji(CustomEmojiHandler.getRichEmoji("refresh"));

        Button profile = Button.primary(id + "-lol", " ").withEmoji(CustomEmojiHandler.getRichEmoji("user"));
        Button opgg = Button.primary(id + "-match", " ").withEmoji(CustomEmojiHandler.getRichEmoji("list2"));
        Button livegame = Button.primary(id + "-rank", " ").withEmoji(CustomEmojiHandler.getRichEmoji("game"));

        switch (id) {
            case "lol":
                profile = profile.asDisabled().withStyle(ButtonStyle.SUCCESS);
                break;
            case "match":
                opgg = opgg.asDisabled().withStyle(ButtonStyle.SUCCESS);
                break;
            case "rank":
                livegame = livegame.asDisabled().withStyle(ButtonStyle.SUCCESS);
                break;
        }

        RiotAccount account = LeagueHandler.getRiotAccountFromSummoner(s);
        Button center = Button.primary(id + "-center-" + s.getAccountId() + "#" + s.getPlatform().name(), account.getName());
        center = center.asDisabled();

        if (user_id != null && LeagueHandler.getNumberOfProfile(user_id) > 1) {
            return List.of(ActionRow.of(left, center, right), ActionRow.of(profile, opgg, livegame, refresh));

        }

        return List.of(ActionRow.of(center, profile, opgg, livegame, refresh));
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
        LeagueHandler.updateSummonerDB(s);
        RiotAccount account = LeagueHandler.getRiotAccountFromSummoner(s);

        EmbedBuilder builder = new EmbedBuilder();
        builder.setAuthor(account.getName() + "#" + account.getTag(), null, LeagueHandler.getSummonerProfilePic(s));
        builder.setColor(Bot.getColor());
        builder.setThumbnail(LeagueHandler.getSummonerProfilePic(s));

        String userId = DatabaseHandler.getUserIdByLOLAccountId(s.getAccountId(), s.getPlatform());
        if(userId != null){

            QueryRecord data = DatabaseHandler.getSummonerData(userId, s.getAccountId());
            if (data.getAsBoolean("tracking")) builder.setFooter("LPs tracking enabled for the current summoner.");
            else builder.setFooter("LPs tracking disabled for the current summoner");
        }

        String description = "Summoner is level **" + s.getSummonerLevel() + "** on " + LeagueHandler.getShardFlag(s.getPlatform()) + s.getPlatform().getRealmValue() + " server.";
        builder.setDescription(description);

        builder.addField("Solo/duo Queue", LeagueHandler.getSoloQStats(s), true);
        builder.addField("Flex Queue", LeagueHandler.getFlexStats(s), true);
        String masteryString = "";
        for(int i = 1; i < 4; i++)
            masteryString += LeagueHandler.getMastery(s, i) + "\n";

        builder.addField("Highest Masteries", masteryString, false);

        long[] split = LeagueHandler.getCurrentSplitRange();
        QueryCollection advanceData = DatabaseHandler.getAdvancedLOLData(s.getAccountId(), split[0], split[1]);

        if (!advanceData.isEmpty()) {
            LeagueEntry entry = LeagueHandler.getRankEntry(s.getSummonerId(), s.getPlatform());

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

            int totalGamesAnalized = advanceData.arrayColumn("games").stream().mapToInt(Integer::parseInt).sum();

            String laneString = "";
            for (LaneType lane : laneStats.keySet()) {
                String wins = laneStats.get(lane).split("-")[0];
                String losses = laneStats.get(lane).split("-")[1];
                int games = Integer.valueOf(wins) + Integer.valueOf(losses);

                if (lane == LaneType.NONE) {
                    laneString += LeagueHandler.getLaneTypeEmoji(lane) + " " + LeagueHandler.getPrettyName(lane) + " " + games + " games\n";
                    continue;
                }

                String percent = String.format("%.2f", Double.parseDouble(wins) * 100 / (Double.parseDouble(wins) + Double.parseDouble(losses)));
                laneString += LeagueHandler.getLaneTypeEmoji(lane) + " " + LeagueHandler.getPrettyName(lane) + " " + games + " games (" +  wins + "W/" + losses + "L) - " + percent +"% WR\n";
            }

            String totalGames = entry != null ? String.valueOf(entry.getWins() + entry.getLosses()) : "0 (placements dont count)";
            builder.addField("Games", "The bot has analyzed " + totalGamesAnalized +" games over the " + totalGames + " you have played this split.\n" + laneString , false);

            String champStats = "";
            for (int i = 0; i < 5 && i < advanceData.size(); i++) {
                QueryRecord row = advanceData.get(i);
                champStats += formatAdvancedData(row);
            }
            builder.addField("Champions", champStats, false);
        }

        builder.addField("Activity", LeagueHandler.getActivity(s), true);

        return builder;
    }

    private static String formatAdvancedData(QueryRecord data) {
        StaticChampion champion = LeagueHandler.getChampionById(data.getAsInt("champion"));
        return CustomEmojiHandler.getFormattedEmoji(champion.getName()) + " " + champion.getName() + ": " + (data.getAsInt("wins") + data.getAsInt("losses")) + " games (" + data.get("wins") + "W/" + data.get("losses") + "L) - " + (data.getAsInt("wins") * 100 / (data.getAsInt("wins") + data.getAsInt("losses"))) + "% WR | " + data.get("total_lp_gain") + " LP\n"
            + "`Avg. KDA " + String.format("%.2f", data.getAsDouble("avg_kills")) + "/" + String.format("%.2f", data.getAsDouble("avg_deaths")) + "/" + String.format("%.2f", data.getAsDouble("avg_assists")) + "`\n";
    }

    public static List<LayoutComponent> getSummonerButtons(Summoner s, String user_id) {
        List<LayoutComponent> buttons = new ArrayList<>(composeButtons(s, user_id, "lol"));
        List<Summoner> summoners = LeagueHandler.getSummonersFromPuuid(s.getPUUID());
        if (summoners.size() == 1) return buttons;

        List<Button> shard = new ArrayList<>();
        for (Summoner summoner : summoners) {
            Button button = Button.primary("lol-shard-" + summoner.getAccountId() + "#" + summoner.getPlatform().name(), summoner.getPlatform().getRealmValue().toUpperCase()).withEmoji(CustomEmojiHandler.getRichEmoji(summoner.getPlatform().getRealmValue().toUpperCase() + "_server"));
            if (summoner.getAccountId().equals(s.getAccountId())) button = button.asDisabled().withStyle(ButtonStyle.SUCCESS);
            shard.add(button);
        }
        buttons.add(0, ActionRow.of(shard));
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

    public static LayoutComponent getOpggQueueTypeButtons(GameQueueType queue) {
        GameQueueType currentGameQueueType = GameQueueType.CHERRY;

        Button soloQ = Button.primary("match-queue-" + GameQueueType.TEAM_BUILDER_RANKED_SOLO, "Solo/Duo");
        Button flex = Button.primary("match-queue-" + GameQueueType.RANKED_FLEX_SR, "Flex");
        Button draft = Button.primary("match-queue-" + GameQueueType.TEAM_BUILDER_DRAFT_UNRANKED_5X5, "Draft");
        Button aram = Button.primary("match-queue-" + GameQueueType.ARAM, "ARAM");
        Button curretModeButton = Button.primary("match-queue-" + currentGameQueueType, LeagueHandler.formatMatchName(currentGameQueueType));

        if (queue == null) return ActionRow.of(soloQ, flex, draft, aram, curretModeButton);

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
            case ARAM:
                aram = aram.withStyle(ButtonStyle.SUCCESS);
                break;
            case CHERRY:
            case ULTBOOK:
                curretModeButton = curretModeButton.withStyle(ButtonStyle.SUCCESS);
                break;
            default:
                break;
        }

        return ActionRow.of(soloQ, flex, draft, aram, curretModeButton);
    }

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

                options.add(SelectOption.of(label, summoner.getPlatform().name() + "_" + match.getGameId() + "#" + summoner.getAccountId()).withEmoji(icon).withDescription(description));
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
        eb.setAuthor(me.getRiotIdName() + "#" + me.getRiotIdTagline());
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
                    String rank = LeagueHandler.getRankIcon(LeagueHandler.getRankEntry(mt.getSummonerId(), match.getPlatform()));
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
                                        + "**Damage Dealt to champion**: " + formatNumber(personalstats.get("damageDealt")) + " (" + getPosition(totalStats, personalstats, "damageDealt") + "th in the game)\n"
                                        + "**Damage Taken**: " + formatNumber(personalstats.get("damageTaken")) + " (" + getPosition(totalStats, personalstats, "damageTaken") + "th in the game)\n";

                eb.addField("Personal Stats", personalStatsTxt, false);

                build = CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getSummoner1Id()) + "_") + CustomEmojiHandler.getFormattedEmoji("a" + String.valueOf(me.getPlayerAugment1())) + " " + CustomEmojiHandler.getFormattedEmoji("a" + String.valueOf(me.getPlayerAugment2())) + "\n"
                        + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getSummoner2Id()) + "_") + CustomEmojiHandler.getFormattedEmoji("a" + String.valueOf(me.getPlayerAugment3())) + " " + CustomEmojiHandler.getFormattedEmoji("a" + String.valueOf(me.getPlayerAugment4())) + "\n"
                        + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem0())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem1())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem2())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem3())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem4())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem5())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem6()));
                eb.addField("Build", build, false);
                break;
            default:
                String blueSide = "";
                String redSide = "";

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

                    String rank = LeagueHandler.getRankIcon(LeagueHandler.getRankEntry(partecipant.getSummonerId(), match.getPlatform()));
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

                String killsIcon = CustomEmojiHandler.getFormattedEmoji("kda");
                String goldIcon = CustomEmojiHandler.getFormattedEmoji("golds2");
                String towericon = CustomEmojiHandler.getFormattedEmoji("tower");

                blueSide += killsIcon + teamStats.get(blue).get("kills") + " ∙ " + towericon + teamStats.get(blue).get("towers") + " ∙ " + goldIcon + " " + formatNumber(teamStats.get(blue).get("gold")) + "\n" + teamStats.get(blue).get("bans") + "\n\n" + teamStats.get(blue).get("champions");
                redSide += killsIcon + teamStats.get(red).get("kills") + " ∙ " + towericon + teamStats.get(red).get("towers") + " ∙ " + goldIcon + " " + formatNumber(teamStats.get(red).get("gold")) + "\n" + teamStats.get(red).get("bans") + "\n\n" + teamStats.get(red).get("champions");
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
                                        + "**Damage Dealt to champion**: " + formatNumber(personalstats.get("damageDealt")) + " (" + getPosition(totalStats, personalstats, "damageDealt") + "th in the game)\n";

                eb.addField("Personal Stats", personalStatsTxt, false);

                build = CustomEmojiHandler.getFormattedEmoji( String.valueOf(me.getSummoner1Id()) + "_") + getFormattedRunes(me, 0) + "\n"
                        + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getSummoner2Id()) + "_") + getFormattedRunes(me, 1) + "\n"
                        + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem0())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem1())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem2())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem3())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem4())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem5())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem6()));
                eb.addField("Build", build, false);

                break;
        }
        return eb;
    }

    private static String getPosition(HashMap<MatchParticipant, HashMap<String, String>> allStats, HashMap<String, String> personalStats, String statKey) {
        int personalStatValue = Integer.parseInt(personalStats.get(statKey));
        int position = 1;

        for (HashMap<String, String> stats : allStats.values()) {
            int statValue = Integer.parseInt(stats.get(statKey));
            if (statValue > personalStatValue) {
                position++;
            }
        }

        return String.valueOf(position);
    }

    private static String formatNumber(String number) {
        return formatNumber(Integer.valueOf(number));
    }

    private static String formatNumber(int number) {
        if (number >= 1000) {
            double value = number / 1000.0;
            DecimalFormat df = new DecimalFormat("#.#");
            return df.format(value) + "k";
        }
        return String.valueOf(number);
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

        for (String gameId : builder.get()) {
            gameIds.add(gameId);
        }

        return gameIds;
    }

    public static EmbedBuilder getOpggEmbed(Summoner s, GameQueueType queue, int index) {
        LeagueShard shard = s.getPlatform();
        RegionShard region = shard.toRegionShard();

        RiotAccount account = LeagueHandler.getRiotAccountFromSummoner(s);
        EmbedBuilder eb = new EmbedBuilder();
        MatchParticipant me = null;
        LOLMatch match = null;
        R4J r4j = LeagueHandler.getRiotApi();

        eb.setAuthor(account.getName() + "#" + account.getTag(), null, LeagueHandler.getSummonerProfilePic(s));
        eb.setTitle("Showing matches from " + LeagueHandler.getShardFlag(shard) + " " + shard.getRealmValue());
        eb.setColor(Bot.getColor());

        List<String> gameIds = getMatchIds(s, queue, index);

        QueryCollection result = DatabaseHandler.getSummonerData(s.getAccountId());

        for(int i = 0; i < 5 && i < gameIds.size(); i++){
            try {

                match = r4j.getLoLAPI().getMatchAPI().getMatch(region, gameIds.get(i));
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
                    + date  + " | ** " + getFormattedDuration((match.getGameDuration())) + "**\n"
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
                        + date + " | **"+ getFormattedDuration((match.getGameDuration()))  + "**\n"
                        + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getSummoner1Id()) + "_") + CustomEmojiHandler.getFormattedEmoji("a" + String.valueOf(me.getPlayerAugment1())) + " " + CustomEmojiHandler.getFormattedEmoji("a" + String.valueOf(me.getPlayerAugment2())) + "\n"
                        + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getSummoner2Id()) + "_") + CustomEmojiHandler.getFormattedEmoji("a" + String.valueOf(me.getPlayerAugment3())) + " " + CustomEmojiHandler.getFormattedEmoji("a" + String.valueOf(me.getPlayerAugment4())) + "\n"
                        + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem0())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem1())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem2())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem3())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem4())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem5())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem6()));

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

                        TierDivisionType rank = row.getAsInt("rank") != MatchTracker.UNKNOWN_RANK ? TierDivisionType.values()[row.getAsInt("rank")] : null;

                        String displayRank = getFormatedRank(rank, true);

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
                                + date  + " | ** " + getFormattedDuration((match.getGameDuration())) + "**\n"
                                + CustomEmojiHandler.getFormattedEmoji( String.valueOf(me.getSummoner1Id()) + "_") + getFormattedRunes(me, 0) + "\n"
                                + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getSummoner2Id()) + "_") + getFormattedRunes(me, 1) + "\n"
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

            } catch (Exception e) {
                e.printStackTrace();
                continue;
            }
        }

        if (eb.getFields().size() == 0)
            eb.setDescription("No games found");

        return eb;
    }

    /**
    * Return a string formated as G1, B4, M, GM, C
    *
    */
    private static String getFormatedRank(TierDivisionType rank, boolean withEmoji) {
        if (rank == null) return "";
        String division = rank.getDivision() != null ? rank.getDivision().length() + "" : "";

        if (rank.getDivision().equals("IV"))division = "4";
        else if (rank.ordinal() < 3) division = "";

        String tier = rank.prettyName().charAt(0) + "";

        if(rank == TierDivisionType.MASTER_I) tier = "MS";
        else if (rank == TierDivisionType.GRANDMASTER_I) tier  = "GM";
        else if (rank == TierDivisionType.CHALLENGER_I) tier = "CH";

        if (withEmoji) return CustomEmojiHandler.getFormattedEmoji(rank.getTier()) + tier + division;
        else return tier + division;

    }

    public static List<LayoutComponent> getOpggButtons(Summoner s, String user_id, GameQueueType queue, int index) {
        int order = 0;
        Button left = Button.primary("match-matchleft", " ").withEmoji(CustomEmojiHandler.getRichEmoji("leftarrow"));
        if (index == 0) left = left.asDisabled();

        Button page = Button.primary("match-index-" + index, "Match " + ((index/5)+1)).asDisabled();
        Button right = Button.primary("match-matchright", " ").withEmoji(CustomEmojiHandler.getRichEmoji("rightarrow"));

        List<LayoutComponent> buttons = new ArrayList<>(composeButtons(s, user_id, "match"));

        StringSelectMenu menu = LeagueMessage.getOpggMenu(s, queue, index);
        if (menu != null) {
            buttons.add(0, ActionRow.of(menu));
            order++;
        }

        buttons.add(order, LeagueMessage.getOpggQueueTypeButtons(queue));
        order++;
        buttons.add(order, ActionRow.of(left, page, right));

        return buttons;
    }

    private static String getFormattedDuration(int seconds) {
        int S = seconds % 60;
        int H = seconds / 60;
        int M = H % 60;
        return M + "m: " + S + "s";
    }

    private static String getFormattedRunes(MatchParticipant me, int row) {
        String prova = "";
        PerkStyle perkS = me.getPerks().getPerkStyles().get(row);
        prova += CustomEmojiHandler.getFormattedEmoji(LeagueHandler.getFatherRune(perkS.getSelections().get(0).getPerk()));
        for (PerkSelection perk : perkS.getSelections()) {
            prova += CustomEmojiHandler.getFormattedEmoji(perk.getPerk());
        }
        return prova;

    }

    public static StringSelectMenu getSelectedMatchMenu(LOLMatch match) {
        ArrayList<SelectOption> options = new ArrayList<>();
        for(MatchParticipant p : match.getParticipants()){
            Emoji icon = LeagueHandler.getEmojiByChampion(p.getChampionId());
            options.add(SelectOption.of(p.getRiotIdName() + "#" + p.getRiotIdTagline(), p.getSummonerId() + "#" + match.getPlatform().name()).withEmoji(icon));
        }

        return StringSelectMenu.create("rank-select")
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
                        Summoner s = LeagueHandler.getSummonerBySummonerId(partecipant.getSummonerId(), summoner.getPlatform());
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
                        LeagueEntry entry = LeagueHandler.getEntry(summoner.getCurrentGame().getGameQueueConfig(), partecipant.getSummonerId(), summoner.getPlatform());
                        if (entry != null) {
                            stats = CustomEmojiHandler.getFormattedEmoji(entry.getTier()) + "\n`" + getFormatedRank(entry.getTierDivisionType(), false) + " " + String.valueOf(entry.getLeaguePoints()) + "LP " + Math.ceil((Double.valueOf(entry.getWins())/Double.valueOf(entry.getWins()+entry.getLosses()))*100)+"% WR`";
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
            e.printStackTrace();
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
            options.add(SelectOption.of(p.getRiotId(), p.getSummonerId() + "#" + summoner.getPlatform().name()).withEmoji(icon));
        }

        return StringSelectMenu.create("rank-select")
                .setPlaceholder("Select a summoner")
                .setMaxValues(1)
                .addOptions(options)
                .build();
    }

    public static List<LayoutComponent> getLivegameButtons(Summoner s, String user_id) {
        return composeButtons(s, user_id, "rank");
    }

}
