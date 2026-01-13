package com.safjnest.commands.settings.reward;

import java.util.ArrayList;
import java.util.Collections;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.core.cache.managers.GuildCache;
import com.safjnest.model.guild.GuildData;
import com.safjnest.model.guild.alert.AlertType;
import com.safjnest.model.guild.alert.RewardData;
import com.safjnest.util.AlertMessage;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;

public class Reward extends SlashCommand {

    public Reward(){
        this.name = this.getClass().getSimpleName().replace("Slash", "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();

        commandData.setThings(this);                            
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        String guildId = event.getGuild().getId();

        GuildData gs = GuildCache.getGuildOrPut(guildId);
        
        RewardData lowerReward = (RewardData) gs.getHigherReward(0);

        if(lowerReward == null) {
            event.deferReply().addComponents(AlertMessage.getEmptyAlert(AlertType.REWARD)).useComponentsV2().queue();
            return;
        }

        event.deferReply().addComponents(AlertMessage.build(gs, lowerReward)).useComponentsV2().queue();
    }
    
}
