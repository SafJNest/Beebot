package com.safjnest.core.audio;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;

import com.safjnest.App;
import com.safjnest.core.Bot;
import com.safjnest.util.SettingsLoader;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;

import dev.arbjerg.lavalink.client.LavalinkClient;
import dev.arbjerg.lavalink.client.NodeOptions;
import net.dv8tion.jda.api.entities.Guild;

/**
 * I really would to know what this class does but i think quantum mechanics its
 * easier to explain.
 * 
 * after 2 years i still dont know what this class does
 * 
 * @author <a href="https://github.com/Leon412">Leon412</a>
 */
public class PlayerManager {

    private static PlayerManager INSTANCE;
    private Map<String, GuildMusicManager> guildMusicManagers = new HashMap<>();
    private AudioPlayerManager audioPlayerManager = new DefaultAudioPlayerManager();
    private LavalinkClient lavalink;

    public PlayerManager() {
        SettingsLoader settingsLoader = new SettingsLoader(App.getBot(), null);

        lavalink = new LavalinkClient(Long.parseLong(Bot.getBotId()));
        lavalink.addNode(new NodeOptions.Builder("beebot", URI.create(settingsLoader.getLavalinkHost()), settingsLoader.getLavalinkPassword(), null, 0).build());

        loadYoutube();
        
        AudioSourceManagers.registerRemoteSources(audioPlayerManager);
        AudioSourceManagers.registerLocalSource(audioPlayerManager);
    }

    private void loadYoutube() {
        dev.lavalink.youtube.YoutubeAudioSourceManager youtubeAudioSourceManager = new dev.lavalink.youtube.YoutubeAudioSourceManager();
        audioPlayerManager.registerSourceManager(youtubeAudioSourceManager);
        //add focking rotator
    }

    public static PlayerManager get() {
        return INSTANCE == null ? (INSTANCE = new PlayerManager()) : INSTANCE;
    }

    public GuildMusicManager getGuildMusicManager(Guild guild) {
        return guildMusicManagers.computeIfAbsent(guild.getId(), (identifier) -> {
            GuildMusicManager musicManager = new GuildMusicManager(audioPlayerManager);
            guild.getAudioManager().setSendingHandler(musicManager.getAudioForwarder());
            return musicManager;
        });
    }

    public AudioPlayerManager getAudioPlayerManager() {
        return audioPlayerManager;
    }

    public Future<Void> loadItemOrdered(Guild guild, String trackURL, AudioLoadResultHandler resultHandler) {
        GuildMusicManager guildMusicManager = getGuildMusicManager(guild);
        return audioPlayerManager.loadItemOrdered(guildMusicManager, trackURL, resultHandler);
    }

}