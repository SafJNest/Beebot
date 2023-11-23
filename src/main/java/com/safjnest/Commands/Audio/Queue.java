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
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.managers.AudioManager;

public class Queue extends Command {
    private String youtubeApiKey;
    private PlayerManager pm;

    public Queue(String youtubeApiKey){
        this.name = this.getClass().getSimpleName().toLowerCase();
        this.aliases = new CommandsLoader().getArray(this.name, "alias");
        this.help = new CommandsLoader().getString(this.name, "help");
        this.cooldown = new CommandsLoader().getCooldown(this.name);
        this.category = new Category(new CommandsLoader().getString(this.name, "category"));
        this.arguments = new CommandsLoader().getString(this.name, "arguments");
        this.youtubeApiKey = youtubeApiKey;
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
            try {
                toPlay = SafJNest.searchYoutubeVideo(event.getArgs(), youtubeApiKey);
            } catch (Exception e) {
                e.printStackTrace();
                event.reply("Couldn't find a video for the given query.");
                return;
            }
        }

        MessageChannel channel = event.getChannel();

        pm = PlayerPool.contains(event.getSelfUser().getId(), guild.getId()) ? PlayerPool.get(event.getSelfUser().getId(), guild.getId()) : PlayerPool.createPlayer(event.getSelfUser().getId(), guild.getId());
        
        pm.getAudioPlayerManager().loadItem(toPlay, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                pm.getTrackScheduler().addQueue(track);

                AudioManager audioManager = guild.getAudioManager();
                audioManager.setSendingHandler(pm.getAudioHandler());
                audioManager.openAudioConnection(myChannel);

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
                /*
                 * for (AudioTrack track : playlist.getTracks()) {
                 * trackScheduler.queue(track);
                 * }
                 */
            }
        
            @Override
            public void noMatches() {
                channel.sendMessage("Not found").queue();
                pm.getTrackScheduler().addQueue(null);
            }

            @Override
            public void loadFailed(FriendlyException throwable) {
                event.reply(throwable.getMessage());
            }
        });
    }
}
