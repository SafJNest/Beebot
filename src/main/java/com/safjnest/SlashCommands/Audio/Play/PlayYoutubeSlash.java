package com.safjnest.SlashCommands.Audio.Play;

import java.util.Arrays;

import com.safjnest.Utilities.CommandsLoader;
import com.safjnest.Utilities.Audio.PlayerManager;
import com.safjnest.Utilities.Audio.ReplyType;
import com.safjnest.Utilities.Audio.ResultHandler;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 1.0
 */
public class PlayYoutubeSlash extends SlashCommand {
    private PlayerManager pm;

    public PlayYoutubeSlash(String father) {
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();
        this.help = new CommandsLoader().getString(name, "help", father.toLowerCase());
        this.cooldown = new CommandsLoader().getCooldown(this.name, father.toLowerCase());
        this.category = new Category(new CommandsLoader().getString(father.toLowerCase(), "category"));
        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "video", "Video/Playlist link or video name", true),
            new OptionData(OptionType.BOOLEAN, "force", "Force play", false)
        );
        this.pm = PlayerManager.get();
    }

	@Override
	protected void execute(SlashCommandEvent event) {
        String search = event.getOption("video").getAsString();
        boolean isForced = event.getOption("force") != null && event.getOption("force").getAsBoolean();

        Guild guild = event.getGuild();
        
        AudioChannel myChannel = event.getMember().getVoiceState().getChannel();
        AudioChannel botChannel = guild.getSelfMember().getVoiceState().getChannel();

        if(myChannel == null){
            event.deferReply(true).addContent("You need to be in a voice channel to use this command.").queue();
            return;
        }

        if(botChannel != null && (myChannel != botChannel)){
            event.deferReply(true).addContent("The bot is already being used in another voice channel.").queue();
            return;
        }
        
        pm.loadItemOrdered(guild, search, new ResultHandler(event, false, search, isForced, ReplyType.SEPARATED));
    }
}