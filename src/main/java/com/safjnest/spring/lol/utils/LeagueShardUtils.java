package com.safjnest.spring.lol.utils;

import java.util.ArrayList;
import java.util.List;

import com.R4B.lol.*;
import com.safjnest.model.customemoji.CustomEmojiHandler;

import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class LeagueShardUtils {

    public static OptionData getAsOptions(boolean required) {
        List<Choice> choices = new ArrayList<>();
        for (LeagueShard shard : getActives()) {
            choices.add(new Choice(shard.name(), shard.name()));
        }
        return new OptionData(OptionType.STRING, "region", "Region you want to get the summoner from", required).addChoices(choices);
    }

    public static OptionData getAsOptions() {
        return getAsOptions(false);
    }

    public static LeagueShard fromOrdinal(int ordinal) {
        try { return LeagueShard.values()[ordinal];} 
        catch (Exception e) { return null; }
    }

    public static String getRegionFlag(LeagueShard shard) {
        return CustomEmojiHandler.getFormattedEmoji(shard.getRealmValue().toUpperCase() + "_server");
    }

    public static String leaderboardScope(LeagueShard shard) {
        return shard == null ? LeagueConstants.GLOBAL_REGION : shard.name();
    }

    public static String defaultRegion(LeagueShard shard) {
        return shard == null ? LeagueConstants.GLOBAL_REGION : shard.name();
    }

    public static List<LeagueShard> getActives() {
        return List.of(
            LeagueShard.EUW1,
            LeagueShard.NA1,
            LeagueShard.KR,
            LeagueShard.EUN1,
            LeagueShard.JP1,
            LeagueShard.BR1,
            LeagueShard.LA1,
            LeagueShard.LA2,
            LeagueShard.TR1,
            LeagueShard.RU,
            LeagueShard.OC1,
            LeagueShard.VN2,
            LeagueShard.SG2,
            LeagueShard.TW2,
            LeagueShard.ME1
        );
    }
}
