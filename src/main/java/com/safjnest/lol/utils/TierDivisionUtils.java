package com.safjnest.lol.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import no.stelar7.api.r4j.basic.constants.types.lol.TierDivisionType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierType;

public class TierDivisionUtils {

  public static boolean isHighElo(TierDivisionType division) {
    return Arrays.asList(TierDivisionType.MASTER_I, TierDivisionType.GRANDMASTER_I, TierDivisionType.CHALLENGER_I).contains(division);
  }

  public static TierType getAverageRank(List<TierDivisionType> divisions) {
    if (divisions == null || divisions.size() == 0) return TierType.UNRANKED; 
    
    int average = 0;
    TierDivisionType averageRank = TierDivisionType.UNRANKED;

    int unranked = 0;
    for (TierDivisionType division : divisions) {
        if (TierDivisionType.UNRANKED == division) {
            unranked++;
            continue;
        }

        average += division.ordinal();
    }
    average = (divisions.size() - unranked) > 0 ? Math.round(average / (divisions.size() - unranked)) : TierDivisionType.UNRANKED.ordinal();

    if (average >= TierDivisionType.values().length)
        average = TierDivisionType.UNRANKED.ordinal();

    averageRank = TierDivisionType.values()[average];
    if (averageRank.getDivision() != null && averageRank.getDivision().equalsIgnoreCase("V")) {
        if (average - 1 < TierDivisionType.values().length) {
            averageRank = TierDivisionType.values()[average - 1];
        }
    }
    return averageRank.getTier() != null ? TierType.valueOf(averageRank.getTier().toUpperCase()) : TierType.UNRANKED;
  }

  public static int getMmr(TierDivisionType division, int lp) {
    if (division == null || division == TierDivisionType.UNRANKED) return -1;

    int base = switch (division) {
        case IRON_IV -> 0;
        case IRON_III -> 100;
        case IRON_II -> 200;
        case IRON_I -> 300;
        case BRONZE_IV -> 400;
        case BRONZE_III -> 500;
        case BRONZE_II -> 600;
        case BRONZE_I -> 700;
        case SILVER_IV -> 800;
        case SILVER_III -> 900;
        case SILVER_II -> 1000;
        case SILVER_I -> 1100;
        case GOLD_IV -> 1200;
        case GOLD_III -> 1300;
        case GOLD_II -> 1400;
        case GOLD_I -> 1500;
        case PLATINUM_IV -> 1600;
        case PLATINUM_III -> 1700;
        case PLATINUM_II -> 1800;
        case PLATINUM_I -> 1900;
        case EMERALD_IV -> 2000;
        case EMERALD_III -> 2100;
        case EMERALD_II -> 2200;
        case EMERALD_I -> 2300;
        case DIAMOND_IV -> 2400;
        case DIAMOND_III -> 2500;
        case DIAMOND_II -> 2600;
        case DIAMOND_I -> 2700;
        case MASTER_I -> 10000;
        case GRANDMASTER_I -> 20000;
        case CHALLENGER_I -> 30000;
        default -> -1;
    };

    return base < 0 ? -1 : base + Math.max(lp, 0);
  }

  public static List<TierType> getHigherTiers(TierType tier) {
    return Arrays.asList(TierType.values()).stream()
      .filter(t -> t.ordinal() <= tier.ordinal())
      .collect(Collectors.toList());
  }

  public static OptionData getAsOptions(boolean required) {
    List<Choice> choices = new ArrayList<>();
    for (TierType tier : getHigherTiers(TierType.IRON)) {
        choices.add(new Choice(tier.name(), tier.name()));
    }
    return new OptionData(OptionType.STRING, "rank", "Minimum rank (default Emerald+)", required).addChoices(choices);
  }

  public static OptionData getAsOptions() {
      return getAsOptions(false);
  }
  
}
