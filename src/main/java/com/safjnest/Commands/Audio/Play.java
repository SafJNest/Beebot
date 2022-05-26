package com.safjnest.Commands.Audio;

import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.safjnest.Utilities.SafJNest;
import com.safjnest.Utilities.TrackScheduler;
import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.Utilities.AudioHandler;
import com.safjnest.Utilities.JSONReader;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.AudioChannel;
import net.dv8tion.jda.api.managers.AudioManager;
import net.dv8tion.jda.api.entities.MessageChannel;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 1.0
 */
public class Play extends Command {
    private String youtubeApiKey;
    private HashMap<String,String> tierOneLink;

    public Play(String youtubeApiKey, HashMap<String,String> tierOneLink){
        this.name = this.getClass().getSimpleName();
        this.aliases = new JSONReader().getArray(this.name, "alias");
        this.help = new JSONReader().getString(this.name, "help");
        this.cooldown = new JSONReader().getCooldown(this.name);
        this.category = new Category(new JSONReader().getString(this.name, "category"));
        this.arguments = new JSONReader().getString(this.name, "arguments");
        this.youtubeApiKey = youtubeApiKey;
        this.tierOneLink = tierOneLink;
    }

    public static String getVideoIdFromYoutubeUrl(String youtubeUrl) {
        /*
            Possibile Youtube urls.
            http://www.youtube.com/watch?v=dQw4w9WgXcQ
            http://www.youtube.com/embed/dQw4w9WgXcQ
            http://www.youtube.com/v/dQw4w9WgXcQ
            http://www.youtube-nocookie.com/v/dQw4w9WgXcQ?version=3&hl=en_US&rel=0
            http://www.youtube.com/watch?v=dQw4w9WgXcQ
            http://www.youtube.com/watch?feature=player_embedded&v=dQw4w9WgXcQ
            http://www.youtube.com/e/dQw4w9WgXcQ
            http://youtu.be/dQw4w9WgXcQ
        */
        String pattern = "(?<=watch\\?v=|/videos/|embed\\/|youtu.be\\/|\\/v\\/|\\/e\\/|watch\\?v%3D|watch\\?feature=player_embedded&v=|%2Fvideos%2F|embed%\u200C\u200B2F|youtu.be%2F|%2Fv%2F)[^#\\&\\?\\n]*";
        Pattern compiledPattern = Pattern.compile(pattern);
        //url is youtube url for which you want to extract the id.
        Matcher matcher = compiledPattern.matcher(youtubeUrl);
        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }

	@Override
	protected void execute(CommandEvent event) {
        if(event.getMember().getVoiceState().getChannel() == null){
            event.reply("Non sei in un canale vocale.");
            return;
        }

        String toPlay = getVideoIdFromYoutubeUrl(event.getArgs());
        if(toPlay == null){
            String keyword = event.getArgs();
            keyword = keyword.replace(" ", "+");
            String url = "https://www.googleapis.com/youtube/v3/search?part=snippet&maxResults=1&q=" + keyword + "&key=" + youtubeApiKey;
            System.out.println(url);
            try {
                URL theUrl = new URL(url);
                URLConnection request = theUrl.openConnection();
                request.connect();
                JSONParser parser = new JSONParser();
                JSONObject json = (JSONObject) parser.parse(new InputStreamReader((InputStream) request.getContent()));
                JSONArray items = (JSONArray) json.get("items");
                JSONObject item = (JSONObject) items.get(0);
                JSONObject id = (JSONObject) item.get("id");
                toPlay = (String) id.get("videoId");
            } catch (IOException | ParseException e) {
                e.printStackTrace();
            }
        }

        MessageChannel channel = event.getChannel();
        AudioChannel myChannel = event.getMember().getVoiceState().getChannel();
        AudioManager audioManager = event.getGuild().getAudioManager();
        AudioPlayerManager playerManager = new DefaultAudioPlayerManager();
        AudioPlayer player = playerManager.createPlayer();
        AudioHandler audioPlayerSendHandler = new AudioHandler(player);
        audioManager.setSendingHandler(audioPlayerSendHandler);
        audioManager.openAudioConnection(myChannel);
        TrackScheduler trackScheduler = new TrackScheduler(player);
        player.addListener(trackScheduler);
        
        playerManager.registerSourceManager(new YoutubeAudioSourceManager(true));
        
        playerManager.loadItem(toPlay, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                trackScheduler.addQueue(track);
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
                channel.sendMessage("Canzone non trovata").queue();
                trackScheduler.addQueue(null);
            }

            @Override
            public void loadFailed(FriendlyException throwable) {
                event.reply(throwable.getMessage());
            }
        });

        player.playTrack(trackScheduler.getTrack());
        if(player.getPlayingTrack() == null)
            return;

        EmbedBuilder eb = new EmbedBuilder();
        eb = new EmbedBuilder();
        eb.setTitle("In riproduzione:");
        eb.addField("Durata", SafJNest.getFormattedDuration(player.getPlayingTrack().getInfo().length) , true);
        eb.setAuthor(event.getJDA().getSelfUser().getName(), "https://github.com/SafJNest",event.getJDA().getSelfUser().getAvatarUrl());
        eb.setFooter("*Questo non e' rhythm, questa e' perfezione cit. steve jobs (probabilmente)", null);
        eb.setColor(new Color(255, 0, 0));
        eb.setDescription(player.getPlayingTrack().getInfo().title);
        eb.setThumbnail("https://img.youtube.com/vi/" + player.getPlayingTrack().getIdentifier() + "/hqdefault.jpg");
        event.reply(eb.build());
        
        if(tierOneLink.containsKey(player.getPlayingTrack().getIdentifier()))
            channel.sendMessage(tierOneLink.get(player.getPlayingTrack().getIdentifier())).queue();
	}
}