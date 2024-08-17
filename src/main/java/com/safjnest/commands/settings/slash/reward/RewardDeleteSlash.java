package com.safjnest.commands.settings.slash.reward;

import java.util.Arrays;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.core.Bot;
import com.safjnest.model.guild.GuildData;
import com.safjnest.model.guild.alert.AlertType;
import com.safjnest.model.guild.alert.RewardData;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;

import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class RewardDeleteSlash extends SlashCommand {
    
    public RewardDeleteSlash(String father){
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(father).getChild(this.name);

        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        
        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "reward_level", "Select the reward to delete", true)
                .setAutoComplete(true)
        );

        commandData.setThings(this);
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        int rewardLevel = event.getOption("reward_level").getAsInt();

        String guildId = event.getGuild().getId();

        GuildData gs = Bot.getGuildData(guildId);
        
        RewardData reward = (RewardData) gs.getAlert(AlertType.REWARD, rewardLevel);

        if(reward == null) {
            event.deferReply(true).addContent("There is no reward set for this level.").queue();
            return;
        }

        if(!gs.deleteAlert(reward.getType(), rewardLevel)) {
            event.deferReply(true).addContent("Something went wrong.").queue();
            return;
        }

        event.deferReply(false).addContent("The reward has been deleted").queue();
    }
}