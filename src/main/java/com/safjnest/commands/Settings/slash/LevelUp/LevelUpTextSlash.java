package com.safjnest.commands.Settings.slash.LevelUp;

import java.util.Arrays;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.core.Bot;
import com.safjnest.model.guild.GuildData;
import com.safjnest.model.guild.alert.AlertData;
import com.safjnest.model.guild.alert.AlertSendType;
import com.safjnest.model.guild.alert.AlertType;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;

import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class LevelUpTextSlash extends SlashCommand{

    public LevelUpTextSlash(String father){
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(father).getChild(this.name);

        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        
        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "message", "Level up message", true),
            new OptionData(OptionType.STRING, "sendtype", "How the message would be sent", false)
                .addChoice("Channel", String.valueOf(AlertSendType.CHANNEL.ordinal()))
                .addChoice("Private", String.valueOf(AlertSendType.PRIVATE.ordinal()))
                .addChoice("Both", String.valueOf(AlertSendType.BOTH.ordinal())),
            new OptionData(OptionType.STRING, "private_message", "If empty would be use the same message (Must enable the private option (private or both)", false)
        );

        commandData.setThings(this);
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        String message = event.getOption("message") != null ? event.getOption("message").getAsString().replace("'", "''") : null;
        String privateText = event.getOption("private_message") != null ? event.getOption("private_message").getAsString() : null;
        AlertSendType sendType = event.getOption("sendtype") != null ? AlertSendType.values()[event.getOption("sendtype").getAsInt()] : AlertSendType.CHANNEL;
        String guildId = event.getGuild().getId();

        GuildData gs = Bot.getGuildData(guildId);
        
        AlertData level = gs.getAlert(AlertType.LEVEL_UP);


        if(!gs.isExpSystemEnabled()) {
            event.deferReply(true).addContent("This guild doesn't have the exp system enabled.").queue();
            return;
        }

        if(level == null) {
            AlertData newLevel = new AlertData(guildId, message, privateText, sendType);
            if (newLevel.getID() == 0) {
                event.deferReply(true).addContent("Something went wrong.").queue();
                return;
            }

            gs.getAlerts().put(newLevel.getKey(), newLevel);
            event.deferReply(false).addContent("Changed level up message.").queue();
            return;
        }


        if(!level.setMessage(message)) {
            event.deferReply(true).addContent("Something went wrong.").queue();
            return;
        }
        
        event.deferReply(false).addContent("Changed level up message.").queue();
    }
}