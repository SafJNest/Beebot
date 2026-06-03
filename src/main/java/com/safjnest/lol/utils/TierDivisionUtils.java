package com.safjnest.lol.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
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

  public static TierType getAvarageRank(List<TierDivisionType> divisions) {
    if (divisions == null || divisions.size() == 0) return TierType.UNRANKED; 
    
    int avarage = 0;
    TierDivisionType avarageRank = TierDivisionType.UNRANKED;

    int unranked = 0;
    for (TierDivisionType division : divisions) {
        if (TierDivisionType.UNRANKED == division) {
            unranked++;
            continue;
        }

        avarage += division.ordinal();
    }
    avarage = (divisions.size() - unranked) > 0 ? Math.round(avarage / (divisions.size() - unranked)) : TierDivisionType.UNRANKED.ordinal();

    if (avarage >= TierDivisionType.values().length) 
        avarage = TierDivisionType.UNRANKED.ordinal();

    avarageRank = TierDivisionType.values()[avarage];
    if (avarageRank.getDivision() != null && avarageRank.getDivision().equalsIgnoreCase("V")) {
        if (avarage - 1 < TierDivisionType.values().length) {
            avarageRank = TierDivisionType.values()[avarage - 1];
        }
    }
    return avarageRank.getTier() != null ? TierType.valueOf(avarageRank.getTier().toUpperCase()) : TierType.UNRANKED;
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
