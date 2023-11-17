package com.safjnest.Commands.Audio;

import java.awt.Color;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.safjnest.Utilities.CommandsLoader;
import com.safjnest.Utilities.SafJNest;
import com.safjnest.Utilities.Audio.PlayerManager;
import com.safjnest.Utilities.Audio.PlayerPool;
import com.safjnest.Utilities.Bot.BotSettingsHandler;
import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import org.json.simple.parser.JSONParser;

import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.managers.AudioManager;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 1.0
 */
public class PlayYoutube extends Command {
    private String youtubeApiKey;
    private PlayerManager pm;

    public PlayYoutube(String youtubeApiKey){
        this.name = this.getClass().getSimpleName().toLowerCase();
        this.aliases = new CommandsLoader().getArray(this.name, "alias");
        this.help = new CommandsLoader().getString(this.name, "help");
        this.cooldown = new CommandsLoader().getCooldown(this.name);
        this.category = new Category(new CommandsLoader().getString(this.name, "category"));
        this.arguments = new CommandsLoader().getString(this.name, "arguments");
        this.youtubeApiKey = youtubeApiKey;
    }

    public static String getVideoIdFromYoutubeUrl(String youtubeUrl) {
        //Matches possibile Youtube urls.
        String pattern = "(.*?)(^|\\/|v=)([a-z0-9_-]{11})(.*)?";
        Pattern compiledPattern = Pattern.compile(pattern);
        Matcher matcher = compiledPattern.matcher(youtubeUrl);
        if (matcher.find()) {
            return matcher.group(3);
        }
        return null;
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
            if(pm.getPlayer().getPlayingTrack() == null && pm.getTrackScheduler().getQueueSize() > 0)
                pm.getPlayer().playTrack(pm.getTrackScheduler().getTrack());
            return;
        }

        String toPlay = getVideoIdFromYoutubeUrl(event.getArgs());

        if(toPlay == null){
            try {
                URL theUrl = new URL("https://www.googleapis.com/youtube/v3/search?part=snippet&type=video&maxResults=1&q=" + event.getArgs().replace(" ", "+") + "&key=" + youtubeApiKey);
                URLConnection request = theUrl.openConnection();
                request.connect();
                JSONParser parser = new JSONParser();
                JSONObject json = (JSONObject) parser.parse(new InputStreamReader((InputStream) request.getContent()));
                JSONArray items = (JSONArray) json.get("items");
                JSONObject item = (JSONObject) items.get(0);
                JSONObject id = (JSONObject) item.get("id");
                toPlay = (String) id.get("videoId");
            } catch (Exception e) {
                event.reply("Couldn't find a video for the given search.");
                return;
            }
        }

        MessageChannel channel = event.getChannel();

        pm = PlayerPool.contains(event.getSelfUser().getId(), guild.getId()) ? PlayerPool.get(event.getSelfUser().getId(), guild.getId()) : PlayerPool.createPlayer(event.getSelfUser().getId(), guild.getId());
        
        pm.getAudioPlayerManager().loadItem(toPlay, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                pm.getTrackScheduler().addQueue(track);

                System.out.println(pm.getTrackScheduler().getQueueSize());
                
                if(pm.getPlayer().getPlayingTrack() == null) {
                    pm.getPlayer().playTrack(pm.getTrackScheduler().getTrack());
                }

                AudioManager audioManager = guild.getAudioManager();
                audioManager.setSendingHandler(pm.getAudioHandler());
                audioManager.openAudioConnection(myChannel);

                EmbedBuilder eb = new EmbedBuilder();

                eb.setTitle("Playing now:");
                eb.setDescription("[" + pm.getPlayer().getPlayingTrack().getInfo().title + "](" + pm.getPlayer().getPlayingTrack().getInfo().uri + ")");
                eb.setThumbnail("https://img.youtube.com/vi/" + pm.getPlayer().getPlayingTrack().getIdentifier() + "/hqdefault.jpg");
                eb.setAuthor(event.getJDA().getSelfUser().getName(), "https://github.com/SafJNest",event.getJDA().getSelfUser().getAvatarUrl());
                eb.setColor(Color.decode(BotSettingsHandler.map.get(event.getJDA().getSelfUser().getId()).color));

                eb.addField("Lenght", SafJNest.getFormattedDuration(pm.getPlayer().getPlayingTrack().getInfo().length) , true);

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