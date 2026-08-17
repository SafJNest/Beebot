package com.safjnest.lol.utils;

import java.util.ArrayList;
import java.util.List;

import com.safjnest.model.customemoji.CustomEmojiHandler;

import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.api.regions.RegionShard;

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

    public static String getRegionCode(LeagueShard shard) {
        return switch (shard) {
            case NA1, JP1, BR1, TR1, SG2, PH2, TW2, VN2, TH2 -> shard.getValue();
            case KR, RU -> shard.getRealmValue() + "1";
            default -> shard.getRealmValue();
        };
    }

    public static RegionShard getAccountRegion(LeagueShard shard) {
        return switch (shard) {
            case VN2, OC1, SG2, PH2, TH2, TW2 -> RegionShard.ASIA;
            default -> shard.toRegionShard();
        };
    }

    public static String cacheRegion(LeagueShard shard) {
        return shard.toRegionShard().name();
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