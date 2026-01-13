package com.safjnest.commands.settings.welcome;

import java.util.ArrayList;
import java.util.Collections;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.core.cache.managers.GuildCache;
import com.safjnest.model.guild.GuildData;
import com.safjnest.model.guild.alert.AlertData;
import com.safjnest.model.guild.alert.AlertType;
import com.safjnest.util.AlertMessage;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;

public class Welcome extends SlashCommand{

    public Welcome(){
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

        AlertData welcome = gs.getAlert(AlertType.WELCOME);


        if(welcome == null) {
            event.deferReply().addComponents(AlertMessage.getEmptyAlert(AlertType.WELCOME)).useComponentsV2().queue();
            return;
        }

        event.deferReply().addComponents(AlertMessage.build(gs, welcome)).useComponentsV2().queue();
    }
}