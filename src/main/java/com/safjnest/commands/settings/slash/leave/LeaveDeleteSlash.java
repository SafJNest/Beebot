package com.safjnest.commands.settings.slash.leave;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.core.Bot;
import com.safjnest.model.guild.GuildData;
import com.safjnest.model.guild.alert.AlertData;
import com.safjnest.model.guild.alert.AlertType;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;

public class LeaveDeleteSlash extends SlashCommand {

    public LeaveDeleteSlash(String father){
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

        GuildData gs = Bot.getGuildData(guildId);

        AlertData leave = gs.getAlert(AlertType.LEAVE);

        if(leave == null) {
            event.deferReply(true).addContent("This guild doesn't have a leave message.").queue();
            return;
        }

        if(!gs.deleteAlert(leave.getType())) {
            event.deferReply(true).addContent("Something went wrong.").queue();
            return;
        }

        event.deferReply(false).addContent("leave message deleted.").queue();
    }
}