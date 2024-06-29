package com.safjnest.commands.Misc.slash;

import java.util.Arrays;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.util.CommandsLoader;

import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import com.safjnest.util.Twitch.TwitchClient;

import com.safjnest.sql.DatabaseHandler;

public class TwitchSlash extends SlashCommand{

    public TwitchSlash(){
        this.name = this.getClass().getSimpleName().replace("Slash", "").toLowerCase();
        this.aliases = new CommandsLoader().getArray(this.name, "alias");
        this.help = new CommandsLoader().getString(this.name, "help");
        this.cooldown = new CommandsLoader().getCooldown(this.name);
        this.category = new Category(new CommandsLoader().getString(this.name, "category"));
        
        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "streamer", "Streamer's username", true),
            new OptionData(OptionType.CHANNEL, "channel", "Channel", true)
                .setChannelTypes(ChannelType.TEXT),
            new OptionData(OptionType.ROLE, "role", "Role that will be pinged when the live goes on", false)
        );
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        String streamerUsername = event.getOption("streamer").getAsString();
        String channel = event.getOption("channel").getAsChannel().getId();
        String role = event.getOption("role") == null ? null : event.getOption("role").getAsRole().getId();

        String streamerId = TwitchClient.getStreamerId(streamerUsername);

        if(streamerId == null){
            event.reply("Streamer not found").queue();
            return;
        }

        TwitchClient.registerSubEvent(streamerId);
        DatabaseHandler.setTwitchSubscriptions(streamerId, event.getGuild().getId(), channel, role);

        event.reply("Twitch subscription registered").queue();
    }
}
