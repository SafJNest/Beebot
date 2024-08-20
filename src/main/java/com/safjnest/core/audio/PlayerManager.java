package com.safjnest.core.audio;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import com.safjnest.App;
import com.safjnest.core.Bot;
import com.safjnest.util.SettingsLoader;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.lava.extensions.youtuberotator.YoutubeIpRotatorSetup;
import com.sedmelluq.lava.extensions.youtuberotator.planner.AbstractRoutePlanner;
import com.sedmelluq.lava.extensions.youtuberotator.planner.RotatingIpRoutePlanner;
import com.sedmelluq.lava.extensions.youtuberotator.tools.ip.*;


import dev.arbjerg.lavalink.client.LavalinkClient;
import dev.arbjerg.lavalink.client.NodeOptions;
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
    private LavalinkClient lavalink;

    public PlayerManager() {
        SettingsLoader settingsLoader = new SettingsLoader(App.getBot(), null);

        lavalink = new LavalinkClient(Long.parseLong(Bot.getBotId()));
        lavalink.addNode(new NodeOptions.Builder("beebot", URI.create(settingsLoader.getLavalinkHost()), settingsLoader.getLavalinkPassword(), null, 0).build());

        loadYoutube(settingsLoader);
        
        AudioSourceManagers.registerRemoteSources(audioPlayerManager);
        AudioSourceManagers.registerLocalSource(audioPlayerManager);
    }

    @SuppressWarnings("rawtypes")
    private void loadYoutube(SettingsLoader settingsLoader) {
        dev.lavalink.youtube.YoutubeAudioSourceManager youtubeAudioSourceManager = new dev.lavalink.youtube.YoutubeAudioSourceManager();
        Web.setPoTokenAndVisitorData(settingsLoader.getPoToken(), settingsLoader.getVisitorData());

        audioPlayerManager.registerSourceManager(youtubeAudioSourceManager);

        
        IpBlock ipBlock = new Ipv6Block("2001:470:1f04:3a::/64");
        
        List<IpBlock> ipBlocks = Collections.singletonList(ipBlock);
        
        AbstractRoutePlanner routePlanner = new RotatingIpRoutePlanner(ipBlocks);

        YoutubeIpRotatorSetup rotator = new YoutubeIpRotatorSetup(routePlanner);

        rotator.forConfiguration(youtubeAudioSourceManager.getHttpInterfaceManager(), false)
            .withMainDelegateFilter(youtubeAudioSourceManager.getContextFilter())
            .setup();
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