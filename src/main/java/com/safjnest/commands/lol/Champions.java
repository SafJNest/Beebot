package com.safjnest.commands.lol;

import java.util.ArrayList;
import java.util.Arrays;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.lol.message.LeagueMessage;
import com.safjnest.lol.message.LeagueMessageParameter;
import com.safjnest.lol.message.LeagueMessageType;
import com.safjnest.lol.utils.ChampionUtils;
import com.safjnest.lol.utils.GameQueueTypeUtils;
import com.safjnest.lol.utils.LeagueShardUtils;
import com.safjnest.lol.utils.PatchUtils;
import com.safjnest.lol.utils.TierDivisionUtils;
import com.safjnest.utils.BotCommand;
import com.safjnest.utils.CommandsLoader;
import com.safjnest.utils.SafJNest;

import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierType;
import no.stelar7.api.r4j.pojo.lol.staticdata.champion.StaticChampion;

public class Champions extends SlashCommand {

    public Champions() {
        this.name = this.getClass().getSimpleName().replace("Slash", "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(this.name);

        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();

        this.contexts = new InteractionContextType[]{InteractionContextType.GUILD, InteractionContextType.BOT_DM};

        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "queue", "Queue", false)
                .addChoice("Ranked Solo/Duo", GameQueueType.TEAM_BUILDER_RANKED_SOLO.name())
                .addChoice("Ranked Flex", GameQueueType.RANKED_FLEX_SR.name())
                .addChoice("Draft Pick", GameQueueType.TEAM_BUILDER_DRAFT_UNRANKED_5X5.name())
                .addChoice("ARAM", GameQueueType.ARAM.name())
                .addChoice("Arena", GameQueueType.CHERRY.name()),
            new OptionData(OptionType.STRING, "role", "Champion Role", false)
                .addChoice("Top", LaneType.TOP.name())
                .addChoice("Jungle", LaneType.JUNGLE.name())
                .addChoice("Mid", LaneType.MID.name())
                .addChoice("ADC", LaneType.BOT.name())
                .addChoice("Support", LaneType.UTILITY.name()),
            new OptionData(OptionType.STRING, "sort", "Sort", false)
                .addChoice("Winrate", LeagueMessageType.CHAMPIONS_BY_WINRATE.name())
                .addChoice("Pickrate", LeagueMessageType.CHAMPIONS_BY_PICKRATE.name())
                .addChoice("Banrate", LeagueMessageType.CHAMPIONS_BY_BANRATE.name()),
            new OptionData(OptionType.STRING, "opponent", "Opponent Champion", false).setAutoComplete(true),
            PatchUtils.getAsOptions(),
            LeagueShardUtils.getAsOptions(),
            TierDivisionUtils.getAsOptions(false)
        );

        commandData.setThings(this);
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        event.deferReply(false).queue();

        LeagueMessageType type = LeagueMessageType.CHAMPIONS_BY_WINRATE;
        if (event.getOption("sort") != null)
            type = LeagueMessageType.valueOf(event.getOption("sort").getAsString());

        LeagueMessageParameter parameter = new LeagueMessageParameter(type);

        if (event.getOption("queue") != null)
            parameter.setQueueType(GameQueueType.valueOf(event.getOption("queue").getAsString()));
        else
            parameter.setQueueType(GameQueueType.TEAM_BUILDER_RANKED_SOLO);

        if (event.getOption("role") != null && GameQueueTypeUtils.hasLane(parameter.getQueueType()))
            parameter.setLaneType(LaneType.valueOf(event.getOption("role").getAsString()));

        if (event.getOption("patch") != null)
            parameter.setPatch(event.getOption("patch").getAsString());

        if (event.getOption("region") != null)
            parameter.setRegion(LeagueShard.valueOf(event.getOption("region").getAsString()));

        if (event.getOption("rank") != null) {
            String rank = event.getOption("rank").getAsString();
            parameter.setRank(rank.equals("ALL") ? null : TierType.valueOf(rank));
        }

        if (event.getOption("opponent") != null)
            parameter.setOpponent(getChampionId(event.getOption("opponent").getAsString()));

        LeagueMessage.send(event.getHook(), null, null, 0, parameter);
    }

    private int getChampionId(String name) {
        String championName = SafJNest.findSimilarWord(name, new ArrayList<>(ChampionUtils.getChampionsNames()));
        StaticChampion champion = ChampionUtils.getChampion(championName);
        return champion != null ? champion.getId() : 0;
    }
}
