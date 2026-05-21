package com.safjnest.commands.lol;

import java.util.Arrays;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.lol.message.LeagueMessage;
import com.safjnest.lol.message.LeagueMessageParameter;
import com.safjnest.lol.message.LeagueMessageType;
import com.safjnest.lol.utils.LeagueShardUtils;
import com.safjnest.utils.BotCommand;
import com.safjnest.utils.CommandsLoader;

import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;

public class Champions extends SlashCommand {

  public Champions(){
    this.name = this.getClass().getSimpleName().replace("Slash", "").toLowerCase();

    BotCommand commandData = CommandsLoader.getCommand(this.name);
    
    this.help = commandData.getHelp();
    this.cooldown = commandData.getCooldown();
    this.category = commandData.getCategory();

    this.contexts = new InteractionContextType[]{InteractionContextType.GUILD, InteractionContextType.BOT_DM};
    
    this.options = Arrays.asList(
        new OptionData(OptionType.STRING, "role", "Champion Role", true)
            .addChoice("Top", "TOP")
            .addChoice("Jungle", "JUNGLE")
            .addChoice("Mid", "MID")
            .addChoice("ADC", "ADC")
            .addChoice("Support", "SUPPORT"),
        new OptionData(OptionType.STRING, "opponent", "Opponent Champion", false).setAutoComplete(true),
        new OptionData(OptionType.STRING, "duo", "Duo Champion", false).setAutoComplete(true),
        new OptionData(OptionType.STRING, "patch", "Patch", false)
            .addChoice("16.7", "16.7")
            .addChoice("16.8", "16.8")
            .addChoice("16.9", "16.9"),
        LeagueShardUtils.getAsOptions(),
        new OptionData(OptionType.STRING, "rank", "Rank (Empty for all)", false)
            .addChoice("IRON", "IRON")
            .addChoice("BRONZE", "BRONZE")
            .addChoice("SILVER", "SILVER")
            .addChoice("GOLD", "GOLD")
            .addChoice("PLATINUM", "PLATINUM")
            .addChoice("DIAMOND", "DIAMOND")
            .addChoice("MASTER", "MASTER")
            .addChoice("GRANDMASTER", "GRANDMASTER")
            .addChoice("CHALLENGER", "CHALLENGER")
    );

    commandData.setThings(this);
}

  @Override
  protected void execute(SlashCommandEvent event) {
    event.deferReply(false).queue();

    LeagueMessageParameter parameter = new LeagueMessageParameter(LeagueMessageType.CHAMPIONS_BY_WINRATE);

    if (event.getOption("role") != null) {
      parameter.setLaneType(LaneType.valueOf(event.getOption("role").getAsString()));
    }

    if (event.getOption("patch") != null) {
      parameter.setPatch(event.getOption("patch").getAsString());
    }
    else {
      parameter.setPatch("16.9");
    }


    LeagueMessage.send(event.getHook(), null, null, 0, parameter);
  }
}
