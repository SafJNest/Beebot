package com.safjnest.commands.settings.reward;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.model.guild.GuildData;
import com.safjnest.model.guild.alert.AlertData;
import com.safjnest.model.guild.alert.AlertSendType;
import com.safjnest.model.guild.alert.AlertType;
import com.safjnest.util.AlertMessage;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;
import com.safjnest.core.cache.managers.GuildCache;

public class RewardCreate extends SlashCommand{

    public RewardCreate(String father){
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(father).getChild(this.name);

        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        
        commandData.setThings(this);
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        String guildId = event.getGuild().getId();
        GuildData gs = GuildCache.getGuildOrPut(guildId);
        AlertData alert = gs.getAlert(AlertType.LEVEL_UP);
        if(alert == null) {
            alert =  new AlertData(guildId, "", "", null, AlertSendType.CHANNEL, AlertType.LEVEL_UP);
            gs.getAlerts().put(alert.getKey(), alert);
        }
        event.deferReply().addComponents(AlertMessage.build(gs, alert)).useComponentsV2().queue();
    }
}