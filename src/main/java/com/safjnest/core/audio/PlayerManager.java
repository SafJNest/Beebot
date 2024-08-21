package com.safjnest.core.audio;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import com.github.topi314.lavalyrics.LyricsManager;
import com.github.topi314.lavalyrics.lyrics.AudioLyrics;
import com.github.topi314.lavasrc.mirror.DefaultMirroringAudioTrackResolver;
import com.github.topi314.lavasrc.spotify.SpotifySourceManager;
import com.safjnest.App;
import com.safjnest.util.SettingsLoader;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.lava.extensions.youtuberotator.YoutubeIpRotatorSetup;
import com.sedmelluq.lava.extensions.youtuberotator.planner.AbstractRoutePlanner;
import com.sedmelluq.lava.extensions.youtuberotator.planner.RotatingIpRoutePlanner;
import com.sedmelluq.lava.extensions.youtuberotator.tools.ip.IpBlock;
import com.sedmelluq.lava.extensions.youtuberotator.tools.ip.Ipv6Block;

import dev.lavalink.youtube.clients.Web;
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
    LyricsManager lyricsManager;
    //private LavalinkClient lavalink; //i hope we dont have to use this bullshit

    public PlayerManager() {
        //lavalink = new LavalinkClient(Long.parseLong(Bot.getBotId()));
        //lavalink.addNode(new NodeOptions.Builder("beebot", URI.create(settingsLoader.getLavalinkHost()), settingsLoader.getLavalinkPassword(), null, 0).build());

        SettingsLoader settingsLoader = new SettingsLoader(App.getBot());
        lyricsManager = new LyricsManager();

        registerYoutube(settingsLoader);
        registerSpotify(settingsLoader);

        AudioSourceManagers.registerRemoteSources(audioPlayerManager);
        AudioSourceManagers.registerLocalSource(audioPlayerManager);
    }

    //registers youtube source and starts the rotator
    @SuppressWarnings("rawtypes")
    private void registerYoutube(SettingsLoader settingsLoader) {
        dev.lavalink.youtube.YoutubeAudioSourceManager youtube = new dev.lavalink.youtube.YoutubeAudioSourceManager();

        Web.setPoTokenAndVisitorData(settingsLoader.getPoToken(), settingsLoader.getVisitorData());

        audioPlayerManager.registerSourceManager(youtube);

        if(!App.isExtremeTesting()) {
            IpBlock ipBlock = new Ipv6Block(settingsLoader.getIpv6Block());
            List<IpBlock> ipBlocks = Collections.singletonList(ipBlock);
            AbstractRoutePlanner routePlanner = new RotatingIpRoutePlanner(ipBlocks);
            YoutubeIpRotatorSetup rotator = new YoutubeIpRotatorSetup(routePlanner);
            rotator.forConfiguration(youtube.getHttpInterfaceManager(), false)
                .withMainDelegateFilter(youtube.getContextFilter())
                .setup();
        }
    }

    private void registerSpotify(SettingsLoader settingsLoader) {
        SpotifySourceManager spotify = new SpotifySourceManager(
                settingsLoader.getSpotifyClientID(),
                settingsLoader.getSpotifyClientSecret(),
                settingsLoader.getSpotifySPDC(),
                settingsLoader.getSpotifyCountryCode(),
                (Void v) -> audioPlayerManager,
                new DefaultMirroringAudioTrackResolver(null));

        lyricsManager.registerLyricsManager(spotify);

        audioPlayerManager.registerSourceManager(spotify);
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

    public AudioLyrics loadLyrics(AudioTrack track) {
        return lyricsManager.loadLyrics(track);
    }

}