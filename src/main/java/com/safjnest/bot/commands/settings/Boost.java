package com.safjnest.bot.commands.settings;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.bot.cache.managers.GuildCache;
import com.safjnest.bot.model.guild.GuildData;
import com.safjnest.bot.model.guild.alert.AlertData;
import com.safjnest.bot.model.guild.alert.AlertType;
import com.safjnest.utils.AlertMessage;
import com.safjnest.utils.BotCommand;
import com.safjnest.utils.CommandsLoader;

public class Boost extends SlashCommand {

    public Boost(){
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

        AlertData boost = gs.getAlert(AlertType.BOOST);

        if(boost == null) {
            event.deferReply().addComponents(AlertMessage.getEmptyAlert(AlertType.BOOST)).useComponentsV2().queue();
            return;
        }

        event.deferReply().addComponents(AlertMessage.build(gs, boost)).useComponentsV2().queue();
    }
    
}
