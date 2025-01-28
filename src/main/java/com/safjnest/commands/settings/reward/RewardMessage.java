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

public class RewardMessage extends SlashCommand {
    
    public RewardMessage(String father){
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(father).getChild(this.name);

        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        
        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "reward_level", "Select the reward to change", true)
                .setAutoComplete(true),
            new OptionData(OptionType.STRING, "message", "Welcome message", true),
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
        
        int rewardLevel = event.getOption("reward_level").getAsInt();

        String guildId = event.getGuild().getId();

        GuildData gs = GuildCache.getGuild(guildId);
        
        RewardData reward = (RewardData) gs.getAlert(AlertType.REWARD, rewardLevel);

        if(reward == null) {
            event.deferReply(true).addContent("There is no reward set for this level.").queue();
            return;
        }

        if(type.equals("channel") && !reward.setMessage(message)) {
            event.deferReply(true).addContent("Something went wrong.").queue();
            return;
        }
        else if (type.equals("private") && !reward.setPrivateMessage(message)){
            event.deferReply(true).addContent("Something went wrong.").queue();
            return;
        }

        if (type.equals("private") && reward.getSendType() == AlertSendType.CHANNEL) 
            reward.setSendType(AlertSendType.BOTH);

        event.deferReply(false).addContent("Changed reward message.").queue();
    }
}