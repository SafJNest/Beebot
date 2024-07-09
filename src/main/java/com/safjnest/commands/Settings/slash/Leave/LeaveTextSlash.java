package com.safjnest.commands.Settings.slash.Leave;

import java.util.Arrays;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.core.Bot;
import com.safjnest.model.guild.GuildData;
import com.safjnest.model.guild.alert.AlertData;
import com.safjnest.model.guild.alert.AlertType;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;

import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class LeaveTextSlash extends SlashCommand {
    
    public LeaveTextSlash(String father){
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(father).getChild(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        
        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "message", "Leave message", true),
            new OptionData(OptionType.STRING, "type", "The type of message to change", false)
                .addChoice("channel", "channel")
                .addChoice("private", "private")
        );

        commandData.setThings(this);
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        String message = event.getOption("message") != null ? event.getOption("message").getAsString() : null;
        String type = event.getOption("type") != null ? event.getOption("type").getAsString() : "channel";

        String guildId = event.getGuild().getId();

        GuildData gs = Bot.getGuildData(guildId);

        AlertData leave = gs.getAlert(AlertType.LEAVE);

        if(leave == null) {
            event.deferReply(true).addContent("This guild doesn't have a leave message. Use the create command.").queue();
            return;
        }

        if(type.equals("channel") && !leave.setMessage(message)) {
            event.deferReply(true).addContent("Something went wrong.").queue();
            return;
        }
        else if (type.equals("private") && !leave.setPrivateMessage(message)){
            event.deferReply(true).addContent("Something went wrong.").queue();
            return;
        }

        event.deferReply(false).addContent("Changed leave message.").queue();
    }
}