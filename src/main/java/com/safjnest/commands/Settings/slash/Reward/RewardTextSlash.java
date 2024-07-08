package com.safjnest.commands.Settings.slash.Reward;

import java.util.Arrays;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.core.Bot;
import com.safjnest.model.guild.GuildData;
import com.safjnest.model.guild.alert.AlertType;
import com.safjnest.model.guild.alert.RewardData;
import com.safjnest.util.CommandsLoader;

import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class RewardTextSlash extends SlashCommand {
    
    public RewardTextSlash(String father){
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();
        this.help = new CommandsLoader().getString(name, "help", father.toLowerCase());
        this.cooldown = new CommandsLoader().getCooldown(this.name, father.toLowerCase());
        this.category = new Category(new CommandsLoader().getString(father.toLowerCase(), "category"));
        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "reward_level", "Select the reward to change", true)
                .setAutoComplete(true),
            new OptionData(OptionType.STRING, "message", "Welcome message", true),
            new OptionData(OptionType.STRING, "type", "The type of message to change", false)
                .addChoice("channel", "channel")
                .addChoice("private", "private")
        );
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        String message = event.getOption("message") != null ? event.getOption("message").getAsString() : null;
        String type = event.getOption("type") != null ? event.getOption("type").getAsString() : "channel";
        
        int rewardLevel = event.getOption("reward_level").getAsInt();

        String guildId = event.getGuild().getId();

        GuildData gs = Bot.getGuildData(guildId);
        
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

        event.deferReply(false).addContent("Changed reward message.").queue();
    }
}