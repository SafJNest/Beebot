package com.safjnest.SlashCommands.Audio.Play;

import java.awt.Color;
import java.util.Arrays;

import com.safjnest.Utilities.CommandsLoader;
import com.safjnest.Utilities.SafJNest;
import com.safjnest.Utilities.Audio.PlayerManager;
import com.safjnest.Utilities.Audio.PlayerPool;
import com.safjnest.Utilities.Bot.BotSettingsHandler;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.managers.AudioManager;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 1.0
 */
public class PlayYoutubeSlash extends SlashCommand {
    private String youtubeApiKey;
    private PlayerManager pm;

    public PlayYoutubeSlash(String youtubeApiKey, String father){
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();
        this.help = new CommandsLoader().getString(name, "help", father.toLowerCase());
        this.cooldown = new CommandsLoader().getCooldown(this.name, father.toLowerCase());
        this.category = new Category(new CommandsLoader().getString(father.toLowerCase(), "category"));
        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "video", "Link or video name", true));
        this.youtubeApiKey = youtubeApiKey;
    }

	@Override
	protected void execute(SlashCommandEvent event) {
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

        String video = event.getOption("video").getAsString();
        String toPlay = SafJNest.getVideoIdFromYoutubeUrl(video);

        if(toPlay == null){
            try {
                toPlay = SafJNest.searchYoutubeVideo(video, youtubeApiKey);
            } catch (Exception e) {
                e.printStackTrace();
                event.reply("Couldn't find a video for the given query.");
                return;
            }
        }

        pm = PlayerPool.contains(event.getJDA().getSelfUser().getId(), guild.getId()) ? PlayerPool.get(event.getJDA().getSelfUser().getId(), guild.getId()) : PlayerPool.createPlayer(event.getJDA().getSelfUser().getId(), guild.getId());
        
        pm.getAudioPlayerManager().loadItem(toPlay, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                pm.getTrackScheduler().addQueue(track);
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
                event.deferReply(true).addContent("Not found").queue();
                pm.getTrackScheduler().addQueue(null);
            }

            @Override
            public void loadFailed(FriendlyException throwable) {
                event.deferReply(true).addContent(throwable.getMessage()).queue();
            }
        });

        pm.getTrackScheduler().nextTrack();
        if(pm.getPlayer().getPlayingTrack() == null) {
            return;
        }

        AudioManager audioManager = guild.getAudioManager();
        audioManager.setSendingHandler(pm.getAudioHandler());
        audioManager.openAudioConnection(myChannel);

        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("Playing now:");
        eb.setAuthor(event.getJDA().getSelfUser().getName(), "https://github.com/SafJNest",event.getJDA().getSelfUser().getAvatarUrl());
        eb.setColor(Color.decode(BotSettingsHandler.map.get(event.getJDA().getSelfUser().getId()).color));
        eb.setDescription("[" + pm.getPlayer().getPlayingTrack().getInfo().title + "](" + pm.getPlayer().getPlayingTrack().getInfo().uri + ")");
        eb.setThumbnail("https://img.youtube.com/vi/" + pm.getPlayer().getPlayingTrack().getIdentifier() + "/hqdefault.jpg");
        eb.addField("Lenght", SafJNest.getFormattedDuration(pm.getPlayer().getPlayingTrack().getInfo().length) , true);

        event.deferReply(false).addEmbeds(eb.build()).queue();
	
    }
}