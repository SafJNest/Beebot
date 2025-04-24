package com.safjnest.commands.settings.reward;

import java.util.Arrays;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.model.guild.GuildData;
import com.safjnest.model.guild.alert.AlertSendType;
import com.safjnest.model.guild.alert.AlertType;
import com.safjnest.model.guild.alert.RewardData;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;

import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import com.safjnest.core.cache.managers.GuildCache;

public class RewardCreate extends SlashCommand{

    public RewardCreate(String father){
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(father).getChild(this.name);

        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        
        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "message", "Leave message", true),
            new OptionData(OptionType.INTEGER, "level", "Level to give the reward", true),
            new OptionData(OptionType.ROLE, "role", "Role to be given.", true),
            new OptionData(OptionType.STRING, "sendtype", "How the message would be sent", false)
                .addChoice("Channel", String.valueOf(AlertSendType.CHANNEL.ordinal()))
                .addChoice("Private", String.valueOf(AlertSendType.PRIVATE.ordinal()))
                .addChoice("Both", String.valueOf(AlertSendType.BOTH.ordinal())),
            new OptionData(OptionType.STRING, "private_message", "If empty would be use the same message (Must enable the private option (private or both)", false),
            new OptionData(OptionType.BOOLEAN, "temporary", "If the role is temporary", false)
        );

        commandData.setThings(this);
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        String message = event.getOption("message").getAsString();
        String privateText = event.getOption("private_message") != null ? event.getOption("private_message").getAsString() : null;

        int level = event.getOption("level").getAsInt();
        String roleId = event.getOption("role").getAsRole().getId();
        boolean temporary = event.getOption("temporary") != null ? event.getOption("temporary").getAsBoolean() : false;
        AlertSendType sendType = event.getOption("sendtype") != null ? AlertSendType.values()[event.getOption("sendtype").getAsInt()] : AlertSendType.CHANNEL;
        
        String guildId = event.getGuild().getId();

        GuildData gs = GuildCache.getGuildOrPut(guildId);

        RewardData reward = gs.getAlert(AlertType.REWARD, level);

        if(reward != null) {
            event.deferReply(true).addContent("A reward already exists for this level.").queue();
            return;
        }

        RewardData newReward = RewardData.createRewardData(guildId, message, privateText, sendType, Arrays.asList(roleId), level, temporary);
        
        if(newReward.getID() == 0) {
            event.deferReply(true).addContent("Something went wrong.").queue();
            return;
        }

        gs.getAlerts().put(newReward.getKey(), newReward);
        event.deferReply(true).addContent("Reward created.").queue();
    }
}