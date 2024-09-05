package com.safjnest.commands.audio.play;

import java.util.Arrays;

import com.safjnest.core.audio.PlayerManager;
import com.safjnest.core.audio.ResultHandler;
import com.safjnest.core.audio.types.*;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;
import com.jagrosh.jdautilities.command.CommandEvent;
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
public class PlayYoutube extends SlashCommand {
    private PlayerManager pm;

    public PlayYoutube(String father) {
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(father).getChild(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "video", "Video/Playlist link or video name", true),
            new OptionData(OptionType.STRING, "timing", "When to play the track", false)
                .addChoice(PlayTiming.NOW.getName(), String.valueOf(PlayTiming.NOW.ordinal()))
                .addChoice(PlayTiming.NEXT.getName(), String.valueOf(PlayTiming.NEXT.ordinal()))
                .addChoice(PlayTiming.LAST.getName() + " (default)", String.valueOf(PlayTiming.LAST.ordinal()))
        );
        this.pm = PlayerManager.get();
        commandData.setThings(this);
    }

    public PlayYoutube() {
        this.name = this.getClass().getSimpleName().toLowerCase().replace("slash", "");

        BotCommand commandData = CommandsLoader.getCommand(this.name);
        
        this.aliases = commandData.getAliases();
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        this.arguments = commandData.getArguments();

        commandData.setThings(this);
        
        this.pm = PlayerManager.get();
    }

	@Override
	protected void execute(SlashCommandEvent event) {
        String search = event.getOption("video").getAsString();
        PlayTiming timing = event.getOption("timing") == null ? PlayTiming.LAST : PlayTiming.values()[event.getOption("timing").getAsInt()];

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
        
        pm.loadItemOrdered(guild, search, new ResultHandler(event, false, search, timing, ReplyType.REPLY));
    }

    @Override   
    protected void execute(CommandEvent event) {
        String search = event.getArgs();
        Guild guild = event.getGuild();
        AudioChannel myChannel = event.getMember().getVoiceState().getChannel();
        AudioChannel botChannel = guild.getSelfMember().getVoiceState().getChannel();
        
        if(myChannel == null){
            event.reply("You need to be in a voice channel to use this command.");
            return;
        }

        if(botChannel != null && myChannel != botChannel){
            event.reply("The bot is already being used in another voice channel.");
            return;
        }

        pm.loadItemOrdered(guild, search, new ResultHandler(event, false, PlayTiming.LAST));
    }
}