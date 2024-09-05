package com.safjnest.commands.audio.slash.play;

import java.io.File;
import java.util.Arrays;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.core.audio.PlayerManager;
import com.safjnest.core.audio.SoundHandler;
import com.safjnest.core.audio.types.AudioType;
import com.safjnest.model.sound.Sound;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;


import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 1.1
 */
public class PlaySoundSlash extends SlashCommand{
    
    private PlayerManager pm;

    public PlaySoundSlash(String father){
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(father).getChild(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();

        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "sound", "Sound to play", true)
                .setAutoComplete(true)
        );
        commandData.setThings(this);
        this.pm = PlayerManager.get();
    }

    public PlaySoundSlash(){
        this.name = this.getClass().getSimpleName().toLowerCase().replace("slash", "");

        BotCommand commandData = CommandsLoader.getCommand(this.name);
        
        this.aliases = commandData.getAliases();
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        this.arguments = commandData.getArguments();
        this.pm = PlayerManager.get();

        commandData.setThings(this);
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        Guild guild = event.getGuild();
        AudioChannel authorChannel = event.getMember().getVoiceState().getChannel();
        AudioChannel botChannel = guild.getSelfMember().getVoiceState().getChannel();
        
        String fileName = event.getOption("sound").getAsString();

        if(authorChannel == null){
            event.reply("You need to be in a voice channel to use this command.");
            return;
        }

        if(botChannel != null && (authorChannel != botChannel)){
            event.reply("The bot is already being used in another voice channel.");
            return;
        }
        
        Sound sound = SoundHandler.getSoundByString(fileName, guild, event.getUser());

        if(sound == null) {
            event.reply("Couldn't find a sound with that name/id.");
            return;
        }

        File soundBoard = new File("rsc" + File.separator + "SoundBoard");

        if(!soundBoard.exists())
            soundBoard.mkdirs();

        fileName = sound.getPath();

        pm.loadItemOrdered(guild, fileName, new ResultHandler(event, sound, fileName));
    }

    @Override
    protected void execute(CommandEvent event) {
        Guild guild = event.getGuild();
        AudioChannel authorChannel = event.getMember().getVoiceState().getChannel();
        AudioChannel botChannel = guild.getSelfMember().getVoiceState().getChannel();
        
        String fileName;
        if((fileName = event.getArgs()).equals("")){
            event.reply("You need to specify a sound name or id.");
            return;
        }

        if(authorChannel == null){
            event.reply("You need to be in a voice channel to use this command.");
            return;
        }

        if(botChannel != null && (authorChannel != botChannel)){
            event.reply("The bot is already being used in another voice channel.");
            return;
        }
        
        Sound sound = SoundHandler.getSoundByString(fileName, guild, event.getAuthor());

        if(sound == null) {
            event.reply("Couldn't find a sound with that name/id.");
            return;
        }

        File soundBoard = new File("rsc" + File.separator + "SoundBoard");

        if(!soundBoard.exists())
            soundBoard.mkdirs();

        fileName = sound.getPath();

        pm.loadItemOrdered(guild, fileName, new ResultHandler(event, sound, fileName));
    }

    private class ResultHandler implements AudioLoadResultHandler {
        private final SlashCommandEvent slashEvent;
        private final CommandEvent commandEvent;
        private final Guild guild;
        private final Member author;
        private final Sound sound;
        
        private ResultHandler(SlashCommandEvent event, Sound sound, String fileName) {
            this.slashEvent = event;
            this.commandEvent = null;
            this.guild = event.getGuild();
            this.author = event.getMember();
            this.sound = sound;
        }

        private ResultHandler(CommandEvent event, Sound sound, String fileName) {
            this.commandEvent = event;
            this.slashEvent = null;
            this.guild = event.getGuild();
            this.author = event.getMember();
            this.sound = sound;
        }
        
        @Override
        public void trackLoaded(AudioTrack track) {
            pm.getGuildMusicManager(guild).getTrackScheduler().play(track, AudioType.SOUND);

            guild.getAudioManager().openAudioConnection(author.getVoiceState().getChannel());

            sound.increaseUserPlays(author.getId());
            sound.setTrack(track);


            if (commandEvent != null) 
                commandEvent.getChannel().sendMessageEmbeds(SoundHandler.getSoundEmbed(sound, author.getUser()).build()).setComponents(SoundHandler.getSoundEmbedButtons(sound)).queue();
            else 
                slashEvent.deferReply(false).addEmbeds(SoundHandler.getSoundEmbed(sound, author.getUser()).build()).addComponents(SoundHandler.getSoundEmbedButtons(sound)).queue();
        }

        @Override
        public void playlistLoaded(AudioPlaylist playlist) {}

        @Override
        public void noMatches() {
            reply("No matches");
        }

        @Override
        public void loadFailed(FriendlyException throwable) {
            reply(throwable.getMessage());
        }

        private void reply(String message) {
            if(commandEvent != null) commandEvent.reply(message);
            else if(slashEvent != null) slashEvent.reply(message).queue();
        }

    }
}