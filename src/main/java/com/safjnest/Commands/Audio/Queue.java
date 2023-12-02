package com.safjnest.Commands.Audio;

import java.awt.Color;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.Utilities.CommandsLoader;
import com.safjnest.Utilities.Audio.PlayerManager;
import com.safjnest.Utilities.Bot.BotSettingsHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;

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
        String query = event.getArgs();
        Guild guild = event.getGuild();
        User self = event.getSelfUser();
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
        
        pm = PlayerManager.get();
        pm.loadItemOrdered(guild, self, query, new ResultHandler(event, false));
    }

    private class ResultHandler implements AudioLoadResultHandler {
        private final CommandEvent event;
        private final Guild guild;
        private final User self;
        private final Member author;
        private final String args;
        private final boolean youtubeSearch;
        
        private ResultHandler(CommandEvent event, boolean youtubeSearch) {
            this.event = event;
            this.guild = event.getGuild();
            this.self = event.getSelfUser();
            this.author = event.getMember();
            this.args = event.getArgs();
            this.youtubeSearch = youtubeSearch;
        }
        
        @Override
        public void trackLoaded(AudioTrack track) {
            pm.getGuildMusicManager(guild, self).getTrackScheduler().queue(track);

            guild.getAudioManager().openAudioConnection(author.getVoiceState().getChannel());

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
            if(youtubeSearch) {
                AudioTrack track = playlist.getTracks().get(0);
                
                pm.getGuildMusicManager(guild, self).getTrackScheduler().queue(track);

                guild.getAudioManager().openAudioConnection(author.getVoiceState().getChannel());

                EmbedBuilder eb = new EmbedBuilder();

                eb.setTitle("Added to queue:");
                eb.setDescription("[" + track.getInfo().title + "](" + track.getInfo().uri + ")");
                eb.setThumbnail("https://img.youtube.com/vi/" + track.getIdentifier() + "/hqdefault.jpg");
                eb.setColor(Color.decode(BotSettingsHandler.map.get(event.getJDA().getSelfUser().getId()).color));
                eb.setFooter("Queued by " + event.getAuthor().getEffectiveName(), event.getAuthor().getAvatarUrl());

                event.reply(eb.build());
            }
            else {
                java.util.List<AudioTrack> tracks = playlist.getTracks();
                for(AudioTrack track : tracks) {
                    pm.getGuildMusicManager(guild, self).getTrackScheduler().queueNoPlay(track);
                }

                guild.getAudioManager().openAudioConnection(author.getVoiceState().getChannel());

                EmbedBuilder eb = new EmbedBuilder();

                eb.setTitle("Playlist queued:");
                eb.setDescription("[" + playlist.getName() + "](" + args + ")");
                eb.setThumbnail("https://img.youtube.com/vi/" + playlist.getTracks().get(0).getIdentifier() + "/hqdefault.jpg");
                eb.setColor(Color.decode(BotSettingsHandler.map.get(event.getJDA().getSelfUser().getId()).color));
                eb.setFooter("Queued by " + event.getAuthor().getEffectiveName(), event.getAuthor().getAvatarUrl());

                event.reply(eb.build());
            }
        }

        @Override
        public void noMatches() {
            if(!youtubeSearch) {
                pm.loadItemOrdered(guild, self, "ytsearch:" + args, new ResultHandler(event, true));
                return;
            }
            event.reply("No matches");
        }

        @Override
        public void loadFailed(FriendlyException throwable) {
            event.reply(throwable.getMessage());
        }
    }
}
