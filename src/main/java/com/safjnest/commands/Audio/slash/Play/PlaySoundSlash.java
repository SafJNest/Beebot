package com.safjnest.commands.Audio.slash.Play;

import java.io.File;
import java.util.Arrays;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.core.audio.PlayerManager;
import com.safjnest.core.audio.SoundBoard;
import com.safjnest.core.audio.types.AudioType;
import com.safjnest.model.Sound;
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
        this.help = new CommandsLoader().getString(name, "help", father.toLowerCase());
        this.cooldown = new CommandsLoader().getCooldown(this.name, father.toLowerCase());
        this.category = new Category(new CommandsLoader().getString(father.toLowerCase(), "category"));
        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "sound", "Sound to play", true)
                .setAutoComplete(true)
        );
        this.pm = PlayerManager.get();
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
        
        Sound sound = SoundBoard.getSoundByString(fileName, guild, event.getUser());

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
        private final SlashCommandEvent event;
        private final Guild guild;
        private final Member author;
        private final Sound sound;
        
        private ResultHandler(SlashCommandEvent event, Sound sound, String fileName) {
            this.event = event;
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



            event.deferReply(false).addEmbeds(SoundBoard.getSoundEmbed(sound, author.getUser()).build()).queue();
        }

        @Override
        public void playlistLoaded(AudioPlaylist playlist) {}

        @Override
        public void noMatches() {
            event.reply("No matches");
        }

        @Override
        public void loadFailed(FriendlyException throwable) {
            event.reply(throwable.getMessage());
        }
    }
}