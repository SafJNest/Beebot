package com.safjnest.util.lol;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import com.safjnest.model.customemoji.CustomEmojiHandler;
import com.safjnest.sql.QueryRecord;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierDivisionType;
import no.stelar7.api.r4j.pojo.lol.championmastery.ChampionMastery;
import no.stelar7.api.r4j.pojo.lol.match.v5.MatchParticipant;
import no.stelar7.api.r4j.pojo.lol.match.v5.PerkSelection;
import no.stelar7.api.r4j.pojo.lol.match.v5.PerkStyle;
import no.stelar7.api.r4j.pojo.lol.staticdata.champion.StaticChampion;

public class LeagueMessageUtils {


    public static String formatAdvancedData(ParticipantChampionStat championStats, ChampionMastery mastery) {
      StaticChampion champion = LeagueHandler.getChampionById(championStats.getChampion());
      if (champion == null) return championStats.getChampion() + "\n";
      int level = (mastery != null ? (mastery.getChampionLevel() >= 10 ? 10 : mastery.getChampionLevel()) : 0);
      return CustomEmojiHandler.getFormattedEmoji("mastery" + level) + " " + CustomEmojiHandler.getFormattedEmoji(champion.getName()) + " **[" + (mastery != null ? mastery.getChampionLevel() : 0) + "]**" + " " + champion.getName() + ": " + (championStats.getWins() + championStats.getLossess()) + " games (" + championStats.getWins() + "W/" + championStats.getLossess() + "L) | " + championStats.getLp() + "LP\n"
          + "`Avg. KDA " + String.format("%.2f", championStats.avgKills()) + "/" + String.format("%.2f", championStats.avgDeaths()) + "/" + String.format("%.2f", championStats.avgAssist()) + "`";
    }

    public static String formatAdvancedData(QueryRecord data, ChampionMastery mastery) {
      StaticChampion champion = LeagueHandler.getChampionById(data.getAsInt("champion"));
      int level = (mastery != null ? (mastery.getChampionLevel() >= 10 ? 10 : mastery.getChampionLevel()) : 0);
      return CustomEmojiHandler.getFormattedEmoji("mastery" + level) + " " + CustomEmojiHandler.getFormattedEmoji(champion.getName()) + " **[" + mastery.getChampionLevel()+ "]**" + " " + champion.getName() + ": " + (data.getAsInt("wins") + data.getAsInt("losses")) + " games (" + data.get("wins") + "W/" + data.get("losses") + "L) | " + data.get("total_lp_gain") + "LP\n"
          + "`Avg. KDA " + String.format("%.2f", data.getAsDouble("avg_kills")) + "/" + String.format("%.2f", data.getAsDouble("avg_deaths")) + "/" + String.format("%.2f", data.getAsDouble("avg_assists")) + "`\n";
    }


    public static String getPosition(HashMap<MatchParticipant, HashMap<String, String>> allStats, HashMap<String, String> personalStats, String statKey) {
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

    public static String formatNumber(String number) {
        return formatNumber(Integer.valueOf(number));
    }

    public static String formatNumber(int number) {
        if (number >= 1000) {
            double value = number / 1000.0;
            DecimalFormat df = new DecimalFormat("#.#");
            return df.format(value) + "k";
        }
        return String.valueOf(number);
    }

    public static String getFormatedRank(TierDivisionType rank, boolean withEmoji) {
        if (rank == null) return "";
        String division = rank.getDivision() != null ? rank.getDivision().length() + "" : "";

        if (division.equals("2") && rank.getDivision().equals("IV")) division = "4";
        else if (rank.ordinal() < 3) division = "";

        String tier = rank.prettyName().charAt(0) + "";

        if(rank == TierDivisionType.MASTER_I) tier = "MS";
        else if (rank == TierDivisionType.GRANDMASTER_I) tier  = "GM";
        else if (rank == TierDivisionType.CHALLENGER_I) tier = "CH";

        if (withEmoji) return CustomEmojiHandler.getFormattedEmoji(rank.getTier()) + tier + division;
        else return tier + division;

    }

    public static String getFormattedDuration(int seconds) {
        int S = seconds % 60;
        int H = seconds / 60;
        int M = H % 60;
        return M + "m: " + S + "s";
    }

    public static String getFormattedDuration(long milliseconds) {
        int S = (int) (milliseconds / 1000) % 60;
        int H = (int) (milliseconds / 1000) / 60;
        int M = H % 60;
        return M + "m: " + S + "s";
    }

    public static String getFormattedRunes(MatchParticipant me, int row) {
        String prova = "";
        PerkStyle perkS = me.getPerks().getPerkStyles().get(row);
        prova += CustomEmojiHandler.getFormattedEmoji(LeagueHandler.getFatherRune(perkS.getSelections().get(0).getPerk()));
        for (PerkSelection perk : perkS.getSelections()) {
            prova += CustomEmojiHandler.getFormattedEmoji(perk.getPerk());
        }
        return prova;

    }

    public static ActionRow getLaneComponents(String prefix, LaneType lane) {
        Button top = Button.secondary(prefix + "-lane-" + LaneType.TOP, "Top").withEmoji(LeagueHandler.getLaneTypeRichEmoji(LaneType.TOP));
        Button jungle = Button.secondary(prefix + "-lane-" + LaneType.JUNGLE, "Jungle").withEmoji(LeagueHandler.getLaneTypeRichEmoji(LaneType.JUNGLE));
        Button mid = Button.secondary(prefix + "-lane-" + LaneType.MID, "Mid").withEmoji(LeagueHandler.getLaneTypeRichEmoji(LaneType.MID));
        Button adc = Button.secondary(prefix + "-lane-" + LaneType.BOT, "ADC").withEmoji(LeagueHandler.getLaneTypeRichEmoji(LaneType.BOT));
        Button support = Button.secondary(prefix + "-lane-" + LaneType.UTILITY, "Support").withEmoji(LeagueHandler.getLaneTypeRichEmoji(LaneType.UTILITY));

        if (lane != null) {
            switch (lane) {
                case TOP:
                    top = top.withStyle(ButtonStyle.SUCCESS);
                    break;
                case JUNGLE:
                    jungle = jungle.withStyle(ButtonStyle.SUCCESS);
                    break;
                case MID:
                    mid = mid.withStyle(ButtonStyle.SUCCESS);
                    break;
                case BOT:
                    adc = adc.withStyle(ButtonStyle.SUCCESS);
                    break;
                case UTILITY:
                    support = support.withStyle(ButtonStyle.SUCCESS);
                    break;
                default:
                    break;
            }
        }

        return ActionRow.of(top, jungle, mid, adc, support);
    }


    public static EmbedBuilder buildMatchups(String prefix, EmbedBuilder eb, HashMap<Integer, int[]> data) {  
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

    public static String getWinrateLabel(List<Entry<Integer, int[]>> data) {
        String label = "";
        int limit = 10;
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

    public static MessageTopLevelComponent getOpggQueueTypeButtons(GameQueueType queue) {
        return getOpggQueueTypeButtons("match", ButtonStyle.SECONDARY, queue);
    }


    public static MessageTopLevelComponent getOpggQueueTypeButtons(String prefix, ButtonStyle defaultStyle, GameQueueType queue) {
        GameQueueType currentGameQueueType = GameQueueType.CHERRY;

        Button soloQ = Button.primary(prefix + "-queue-" + GameQueueType.TEAM_BUILDER_RANKED_SOLO, "Solo/Duo").withStyle(defaultStyle);
        Button flex = Button.primary(prefix + "-queue-" + GameQueueType.RANKED_FLEX_SR, "Flex").withStyle(defaultStyle);
        Button draft = Button.primary(prefix + "-queue-" + GameQueueType.TEAM_BUILDER_DRAFT_UNRANKED_5X5, "Draft").withStyle(defaultStyle);
        Button aram = Button.primary(prefix + "-queue-" + GameQueueType.ARAM, "ARAM").withStyle(defaultStyle);
        Button curretModeButton = Button.primary(prefix + "-queue-" + currentGameQueueType, LeagueHandler.formatMatchName(currentGameQueueType)).withStyle(defaultStyle);

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
            case SWIFTPLAY:
                curretModeButton = curretModeButton.withStyle(ButtonStyle.SUCCESS);
                break;
            default:
                break;
        }

        return ActionRow.of(soloQ, flex, draft, aram, curretModeButton);
    }

}
