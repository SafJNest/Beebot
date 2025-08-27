package com.safjnest.commands.lol.summoner;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import com.github.twitch4j.helix.domain.Team;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.core.Bot;
import com.safjnest.core.cache.managers.UserCache;
import com.safjnest.model.UserData;
import com.safjnest.model.customemoji.CustomEmojiHandler;
import com.safjnest.sql.LeagueDBHandler;
import com.safjnest.sql.QueryCollection;
import com.safjnest.sql.QueryRecord;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;
import com.safjnest.util.lol.LeagueHandler;
import com.safjnest.util.lol.model.MatchData;
import com.safjnest.util.lol.model.ParticipantData;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.basic.constants.types.lol.TeamType;
import no.stelar7.api.r4j.pojo.lol.staticdata.champion.StaticChampion;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @since 1.3
 */
public class SummonerChampion extends SlashCommand {
    
    /**
     * Constructor
     */
    public SummonerChampion(String father){
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(father).getChild(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();

        this.contexts = new InteractionContextType[]{InteractionContextType.GUILD, InteractionContextType.BOT_DM};

        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "summoner", "Name and tag of the summoner you want to link", true).setAutoComplete(true),
            new OptionData(OptionType.STRING, "champion", "Champion Name", true).setAutoComplete(true),
            LeagueHandler.getLeagueShardOptions()
        );
        commandData.setThings(this);
    }

    @Override
    protected void execute(CommandEvent event) {
        //22 iodid
        //50 sunyx
        //150067 uglydemon
        //5 primeegis

        //80 pantheon
        //412 thresh
        //555 pyke
        int summonerId = 50;
        int champion = 555;

        long timeStart = 1736420400000l;
        long timeEnd = System.currentTimeMillis();

        GameQueueType queue = GameQueueType.TEAM_BUILDER_RANKED_SOLO;
        LaneType role = LaneType.UTILITY;
        List<MatchData> matches = null;
        try {
            matches = LeagueDBHandler.getMatchHistory(summonerId, champion, timeStart, timeEnd, queue, role);
        } catch (SQLException e) {
            e.printStackTrace();
        }

        class StatAccumulator {
            int sum = 0;
            int count = 0;
            void add(int value) { sum += value; count++; }
            double avg() { return count == 0 ? 0 : (double) sum / count; }
            public String toString() { return "sum: " + sum + ", count: " + count + ", avg: " + avg(); }
        }


        LinkedHashMap<LaneType, String> laneStats = new LinkedHashMap<>();
        LinkedHashMap<GameQueueType, String> queueStats = new LinkedHashMap<>();
        HashMap<String, StatAccumulator> overallStats = new HashMap<>();

        HashMap<Integer, int[]> laneVsWinrate = new HashMap<>();
        HashMap<Integer, int[]> duoWinrate = new HashMap<>();

        for (MatchData match : matches) {
            for (ParticipantData participant : match.participants) {
                if (participant.summonerId != summonerId) {
                    continue;
                }

                LaneType lane = participant.lane;
                TeamType side = participant.side;
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
    
                overallStats.computeIfAbsent("damage", k -> new StatAccumulator()).add(participant.damage);
                overallStats.computeIfAbsent("damage_building", k -> new StatAccumulator()).add(participant.damageBuilding);
                overallStats.computeIfAbsent("cs", k -> new StatAccumulator()).add(participant.cs);
                overallStats.computeIfAbsent("vision_score", k -> new StatAccumulator()).add(participant.visionScore);
                overallStats.computeIfAbsent("ward", k -> new StatAccumulator()).add(participant.ward);
                overallStats.computeIfAbsent("ward_killed", k -> new StatAccumulator()).add(participant.wardKilled);
                overallStats.computeIfAbsent("gold_earned", k -> new StatAccumulator()).add(participant.goldEarned);
                overallStats.computeIfAbsent("kills", k -> new StatAccumulator()).add(kills);
                overallStats.computeIfAbsent("deaths", k -> new StatAccumulator()).add(deaths);
                overallStats.computeIfAbsent("assists", k -> new StatAccumulator()).add(assists);
                overallStats.computeIfAbsent("cs_min", k -> new StatAccumulator()).add((int) csPerMin);

                int teamKills = match.participants.stream()
                    .filter(p -> p.side == side)
                    .mapToInt(p -> Integer.parseInt(p.kda.split("/")[0]))
                    .sum();
                
                double killParticipation = teamKills == 0 ? 0 : (double) (kills + assists) / teamKills;
                overallStats.computeIfAbsent("kill_participation", k -> new StatAccumulator()).add((int)(killParticipation * 100));

                boolean isDuo = lane == LaneType.BOT || lane == LaneType.UTILITY;

                List<Integer> enemyChamps = match.participants.stream()
                    .filter(p -> p.side != side)
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
                        .filter(p -> p.side == side)
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


        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("Stats for champion " + champion);
        eb.setDescription("Total games: " + matches.size());
        eb.setColor(Bot.getColor());

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
        
        String performace = 
            "**KDA**\n`" + kda + " (" + String.format("%.2f", overallStats.get("kill_participation").avg()) + "% kp)`\n" +
            "**Vision Score**\n`" + visionScore + "`\n" +
            "**CS**\n`" + cs + "`\n" +
            "**Damage**\n`" + damaString + "`\n" +
            "**Gold Earned**\n`" + String.format("%.2f", overallStats.get("gold_earned").avg()) + "`\n";

            
        
        eb.addField("Games", gameString, true);
        eb.addField("Roles", laneString , true);

        eb.addField("Avarage Performace", performace, false);

        eb = getMatchups("matchups", eb, laneVsWinrate);

        if (role == LaneType.BOT || role == LaneType.UTILITY) 
            eb = getMatchups("duo bot", eb, duoWinrate);


        event.reply(eb.build());


    }


    private EmbedBuilder getMatchups(String prefix, EmbedBuilder eb, HashMap<Integer, int[]> data) {  
        List<Map.Entry<Integer, int[]>> worstMatchups = data.entrySet().stream()
            .filter(entry -> (entry.getValue()[0] + entry.getValue()[1]) >= 2)
            .sorted((a, b) -> {
                double winrateA = (double) a.getValue()[0] / (a.getValue()[0] + a.getValue()[1]);
                double winrateB = (double) b.getValue()[0] / (b.getValue()[0] + b.getValue()[1]);
                int gamesA = a.getValue()[0] + a.getValue()[1];
                int gamesB = b.getValue()[0] + b.getValue()[1];
                int cmp = Double.compare(winrateA, winrateB);
                return cmp == 0 ? Integer.compare(gamesB, gamesA) : cmp;
            })
            .collect(Collectors.toList());
        
        List<Map.Entry<Integer, int[]>> bestMatchups = data.entrySet().stream()
            .filter(entry -> (entry.getValue()[0] + entry.getValue()[1]) >= 2)
            .sorted((a, b) -> {
                double winrateA = (double) a.getValue()[0] / (a.getValue()[0] + a.getValue()[1]);
                double winrateB = (double) b.getValue()[0] / (b.getValue()[0] + b.getValue()[1]);
                int gamesA = a.getValue()[0] + a.getValue()[1];
                int gamesB = b.getValue()[0] + b.getValue()[1];
                int cmp = Double.compare(winrateB, winrateA);
                return cmp == 0 ? Integer.compare(gamesB, gamesA) : cmp;
            })
            .collect(Collectors.toList());
        
        List<Map.Entry<Integer, int[]>> popularMatchups = data.entrySet().stream()
            .filter(entry -> (entry.getValue()[0] + entry.getValue()[1]) >= 2)
            .sorted((a, b) -> {
                int gamesA = a.getValue()[0] + a.getValue()[1];
                int gamesB = b.getValue()[0] + b.getValue()[1];
                if (gamesA == gamesB) {
                    double winrateA = (double) a.getValue()[0] / (gamesA);
                    double winrateB = (double) b.getValue()[0] / (gamesB);
                    return Double.compare(winrateB, winrateA);
                }
                return Integer.compare(gamesB, gamesA);
            })
            .collect(Collectors.toList());
        
        eb.addField("Worst " + prefix, getWinrateLabel(worstMatchups), true);
        eb.addField("Best " + prefix, getWinrateLabel(bestMatchups), true);
        eb.addField("Popular " + prefix, getWinrateLabel(popularMatchups), true);
        return eb;
    }

    private String getWinrateLabel(List<Entry<Integer, int[]>> data) {
        String label = "";
        int limit = 4;
        for (Map.Entry<Integer, int[]> entry : data) {
            if (limit-- == 0) break;
            StaticChampion champ = LeagueHandler.getChampionById(entry.getKey());
            if (champ == null) continue;
            int[] val = entry.getValue();
            int totalGames = val[0] + val[1];
            double winrate = (double) val[0] / totalGames * 100.0;
            label += CustomEmojiHandler.getFormattedEmoji(champ.getName()) + " " + champ.getName() + "\n`" + val[0] + "W/" + val[1] + "L (" + String.format("%.1f%%", winrate) + ")`\n";
        }

        return label;
    }


  /**
  * This method is called every time a member executes the command.
  */
  @Override
	protected void execute(SlashCommandEvent event) {
        event.deferReply(false).queue();
        no.stelar7.api.r4j.pojo.lol.summoner.Summoner s = LeagueHandler.getSummonerByArgs(event);
        if(s == null){
            event.getHook().editOriginal("Couldn't find the specified summoner. Remember to specify the tag").queue();
            return;
        }

        String name = event.getOption("summoner").getAsString();

        UserData data = UserCache.getUser(event.getMember().getId());
        if(data.getRiotAccounts().containsKey(s.getPUUID())){
            event.getHook().editOriginal("This account is already connected to your profile.").queue();
            return;
        }

        if (LeagueDBHandler.getUserIdByLOLAccountId(s.getPUUID(), s.getPlatform()) != null) {
            event.getHook().editOriginal("This account is already connected to another profile.\nIf you think someone has linked your account please write to our discord server support or use /bug").queue();
            return;
        }

       

        if (!data.addRiotAccount(s)) {
            event.getHook().editOriginal("Something went wrong while connecting your account.").queue();
            return;
        }

        event.getHook().editOriginal("Connected " + name + " to your profile.").queue();

	}

}
