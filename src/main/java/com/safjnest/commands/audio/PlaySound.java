package com.safjnest.commands.audio;

import java.io.File;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
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

import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 1.0
 */
public class PlaySound extends Command{
    private PlayerManager pm;

    public PlaySound() {
        this.name = this.getClass().getSimpleName().toLowerCase();

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
        private final CommandEvent event;
        private final Guild guild;
        private final Member author;
        private final Sound sound;
        
        private ResultHandler(CommandEvent event, Sound sound, String fileName) {
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
            event.getChannel().sendMessageEmbeds(SoundHandler.getSoundEmbed(sound, author.getUser()).build()).setComponents(SoundHandler.getSoundEmbedButtons(sound)).queue();
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