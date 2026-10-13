package com.safjnest.spring.lol.utils;

import java.util.ArrayList;
import java.util.List;

import com.R4B.lol.*;
import com.safjnest.model.customemoji.CustomEmojiHandler;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class TierDivisionUtils {
  private static final List<TierType> PROFILE_REBUILD_TIERS = List.of(TierType.GRANDMASTER, TierType.CHALLENGER);

  public static List<TierType> profileRebuildTiers() {
    return PROFILE_REBUILD_TIERS;
  }

  public static String getFormattedRank(TierDivisionType rank, boolean withEmoji) {
    if (rank == null) return "";
    String division = rank.getDivision() != null ? String.valueOf(rank.getDivision().length()) : "";
    if (division.equals("2") && rank.getDivision().equals("IV")) division = "4";
    else if (rank.ordinal() < 3) division = "";

    String tier = String.valueOf(rank.prettyName().charAt(0));
    if (rank == TierDivisionType.MASTER_I) tier = "MS";
    else if (rank == TierDivisionType.GRANDMASTER_I) tier = "GM";
    else if (rank == TierDivisionType.CHALLENGER_I) tier = "CH";
    return withEmoji ? CustomEmojiHandler.getFormattedEmoji(rank.getTier()) + tier + division : tier + division;
  }

  public static OptionData getAsOptions(boolean required) {
    List<Choice> choices = new ArrayList<>();
    for (TierType tier : TierType.getHigherTiers(TierType.IRON)) {
        choices.add(new Choice(tier.name(), tier.name()));
    }
    return new OptionData(OptionType.STRING, "rank", "Minimum rank (default Emerald+)", required).addChoices(choices);
  }

  public static OptionData getAsOptions() {
      return getAsOptions(false);
  }
}
