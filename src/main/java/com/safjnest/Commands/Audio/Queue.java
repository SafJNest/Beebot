package com.safjnest.Commands.Audio;

import java.awt.Color;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.Utilities.CommandsLoader;
import com.safjnest.Utilities.SafJNest;
import com.safjnest.Utilities.Audio.PlayerManager;
import com.safjnest.Utilities.Audio.PlayerPool;
import com.safjnest.Utilities.Bot.BotSettingsHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.managers.AudioManager;

public class Queue extends Command {
    private PlayerManager pm;

    public Queue(){
        this.name = this.getClass().getSimpleName().toLowerCase();
        this.aliases = new CommandsLoader().getArray(this.name, "alias");
        this.help = new CommandsLoader().getString(this.name, "help");
        this.cooldown = new CommandsLoader().getCooldown(this.name);
        this.category = new Category(new CommandsLoader().getString(this.name, "category"));
        this.arguments = new CommandsLoader().getString(this.name, "arguments");
    }

	@Override
	protected void execute(CommandEvent event) {
        Guild guild = event.getGuild();
        AudioChannel myChannel = event.getMember().getVoiceState().getChannel();
        AudioChannel botChannel = guild.getSelfMember().getVoiceState().getChannel();
        
        if(myChannel == null){
            event.reply("You need to be in a voice channel to use this command.");
            return;
        }

        if(botChannel != null && (myChannel != botChannel)){
            event.reply("The bot is already being used in another voice channel.");
            return;
        }

        if(event.getArgs().equals("") && PlayerPool.contains(event.getSelfUser().getId(), guild.getId())) {
            pm = PlayerPool.get(event.getSelfUser().getId(), guild.getId());
            pm.getTrackScheduler().prevTrack();
            return;
        }

        String toPlay = SafJNest.getVideoIdFromYoutubeUrl(event.getArgs());

        if(toPlay == null){
            toPlay = "ytsearch:" + event.getArgs();
        }

        pm = PlayerPool.contains(event.getSelfUser().getId(), guild.getId()) ? PlayerPool.get(event.getSelfUser().getId(), guild.getId()) : PlayerPool.createPlayer(event.getSelfUser().getId(), guild.getId());
        
        pm.getAudioPlayerManager().loadItemOrdered(event.getGuild(), toPlay, new ResultHandler(event));
    }

    private class ResultHandler implements AudioLoadResultHandler {
        private final CommandEvent event;
        
        private ResultHandler(CommandEvent event) {
            this.event = event;
        }
        
        @Override
        public void trackLoaded(AudioTrack track) {
            pm.getTrackScheduler().addQueue(track);

            AudioManager audioManager = event.getGuild().getAudioManager();
            audioManager.setSendingHandler(pm.getAudioHandler());
            audioManager.openAudioConnection(event.getMember().getVoiceState().getChannel());

            EmbedBuilder eb = new EmbedBuilder();

            eb.setTitle("Added to queue:");
            eb.setDescription("[" + track.getInfo().title + "](" + track.getInfo().uri + ")");
            eb.setThumbnail("https://img.youtube.com/vi/" + track.getIdentifier() + "/hqdefault.jpg");
            eb.setColor(Color.decode(BotSettingsHandler.map.get(event.getJDA().getSelfUser().getId()).color));
            eb.setFooter("Queued by " + event.getAuthor().getEffectiveName(), event.getAuthor().getAvatarUrl());

            event.reply(eb.build());
        }

        @Override
        public void playlistLoaded(AudioPlaylist playlist) {
            AudioTrack cTrack = null;
            for(AudioTrack track : playlist.getTracks()){
                pm.getTrackScheduler().addQueue(track);
                cTrack = track;;
                break;
            }

            AudioManager audioManager = event.getGuild().getAudioManager();
            audioManager.setSendingHandler(pm.getAudioHandler());
            audioManager.openAudioConnection(event.getMember().getVoiceState().getChannel());

            EmbedBuilder eb = new EmbedBuilder();

            eb.setTitle("Added to queue:");
            eb.setDescription("[" + cTrack.getInfo().title + "](" + cTrack.getInfo().uri + ")");
            eb.setThumbnail("https://img.youtube.com/vi/" + cTrack.getIdentifier() + "/hqdefault.jpg");
            eb.setColor(Color.decode(BotSettingsHandler.map.get(event.getJDA().getSelfUser().getId()).color));
            eb.setFooter("Queued by " + event.getAuthor().getEffectiveName(), event.getAuthor().getAvatarUrl());

            event.reply(eb.build());
        }

        @Override
        public void noMatches() {
            event.getChannel().sendMessage("Not found").queue();
        }

        @Override
        public void loadFailed(FriendlyException throwable) {
            event.reply(throwable.getMessage());
        }
    }
}
