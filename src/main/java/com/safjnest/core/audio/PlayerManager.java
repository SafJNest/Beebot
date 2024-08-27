package com.safjnest.core.audio;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.List;
import java.util.ArrayList;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

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
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.io.MessageInput;
import com.sedmelluq.discord.lavaplayer.tools.io.MessageOutput;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.DecodedTrackHolder;

import dev.lavalink.youtube.clients.Web;
import net.dv8tion.jda.api.entities.Guild;

import com.safjnest.core.audio.types.AudioType;

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
    private void registerYoutube(SettingsLoader settingsLoader) {
        dev.lavalink.youtube.YoutubeAudioSourceManager youtube = new dev.lavalink.youtube.YoutubeAudioSourceManager();

        Web.setPoTokenAndVisitorData(settingsLoader.getPoToken(), settingsLoader.getVisitorData());

        audioPlayerManager.registerSourceManager(youtube);

        // if(!App.isExtremeTesting()) {
        //     IpBlock ipBlock = new Ipv6Block(settingsLoader.getIpv6Block());
        //     List<IpBlock> ipBlocks = Collections.singletonList(ipBlock);
        //     AbstractRoutePlanner routePlanner = new RotatingIpRoutePlanner(ipBlocks);
        //     YoutubeIpRotatorSetup rotator = new YoutubeIpRotatorSetup(routePlanner);
        //     rotator.forConfiguration(youtube.getHttpInterfaceManager(), false)
        //         .withMainDelegateFilter(youtube.getContextFilter())
        //         .setup();
        // }
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

    public AudioTrack createTrack(Guild guild, String uri) {
        GuildMusicManager guildMusicManager = getGuildMusicManager(guild);
        List<AudioTrack> tracks = new ArrayList<>();
        try {
            audioPlayerManager.loadItemOrdered(guildMusicManager, uri, new AudioLoadResultHandler() {
                @Override
                public void trackLoaded(AudioTrack track) {
                    System.out.println("Loaded track: " + track.getInfo().title);
                    tracks.add(track);
                }

                @Override
                public void playlistLoaded(AudioPlaylist playlist) {
                    System.out.println("Loaded playlist: " + playlist.getName());
                    tracks.addAll(playlist.getTracks());
                }

                @Override
                public void noMatches() {
                    System.out.println("No matches found for URI: " + uri);
                }

                @Override
                public void loadFailed(FriendlyException exception) {
                    System.err.println("Failed to load URI: " + uri);
                    exception.printStackTrace();
                }
            }).get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }

        return tracks.size() > 0 ? tracks.get(0) : null;
    }

    public String encodeTrack(AudioTrack track) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        MessageOutput output = new MessageOutput(outputStream);
        try {
            output.startMessage();
            audioPlayerManager.encodeTrack(output, track);
            //output.commitMessage(); //non so se serve mi mette la track due volte se lo uso

            output.finish();
        } catch (IOException e) {
            e.printStackTrace();
        }

        String encodedTrack = Base64.getEncoder().encodeToString(outputStream.toByteArray());

        System.out.println("\n" + encodedTrack);

        return encodedTrack;
    }

    public AudioTrack decodeTrack(String encodedTrack) {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(encodedTrack));
        MessageInput input = new MessageInput(inputStream);
        AudioTrack decodedTrack = null;
        try {
            decodedTrack = audioPlayerManager.decodeTrack(input).decodedTrack;
        } catch (IOException e) {
            e.printStackTrace();
        }

        if(decodedTrack != null) System.out.println("\n" + decodedTrack.getInfo().uri);
        else System.out.println("\n" + "decodedTrack is null :(");

        return decodedTrack;
    }

    public void loadPlaylist(Guild guild, List<String> uris, AudioLoadResultHandler resultHandler) {
        GuildMusicManager guildMusicManager = getGuildMusicManager(guild);
        List<AudioTrack> tracks = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(uris.size());
    
        for (String uri : uris) {
            audioPlayerManager.loadItemOrdered(guildMusicManager, uri, new AudioLoadResultHandler() {
                @Override
                public void trackLoaded(AudioTrack track) {
                    tracks.add(track);
                    System.out.println("Loaded track: " + track.getInfo().title);
                    latch.countDown();
                }
    
                @Override
                public void playlistLoaded(AudioPlaylist playlist) {
                    System.out.println("Loaded playlist: " + playlist.getName());
                    tracks.addAll(playlist.getTracks());
                    latch.countDown();
                }
    
                @Override
                public void noMatches() {
                    System.out.println("No matches found for URI: " + uri);
                    latch.countDown();
                }
    
                @Override
                public void loadFailed(FriendlyException exception) {
                    System.err.println("Failed to load URI: " + uri);
                    exception.printStackTrace();
                    latch.countDown();
                }
            });
        }
    
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    
        SafjAudioPlaylist playlist = new SafjAudioPlaylist("Custom Playlist", tracks, null);
        resultHandler.playlistLoaded(playlist);
    }

    public AudioLyrics loadLyrics(AudioTrack track) {
        return lyricsManager.loadLyrics(track);
    }

}