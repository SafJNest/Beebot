package com.safjnest.commands.Audio.slash.Play;

import java.util.Arrays;

import com.safjnest.core.audio.PlayerManager;
import com.safjnest.core.audio.ResultHandler;
import com.safjnest.core.audio.types.ReplyType;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;
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

        BotCommand commandData = CommandsLoader.getCommand(father).getChild(this.name);
        
        this.aliases = commandData.getAliases();
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        this.arguments = commandData.getArguments();
        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "video", "Video/Playlist link or video name", true),
            new OptionData(OptionType.BOOLEAN, "force", "Force play", false)
        );
        this.pm = PlayerManager.get();
        commandData.setThings(this);
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
        
        pm.loadItemOrdered(guild, search, new ResultHandler(event, false, search, isForced, ReplyType.REPLY));
    }
}