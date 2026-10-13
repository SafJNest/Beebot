package com.safjnest.utils.twitch;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import com.github.philippheuer.events4j.api.domain.IEventSubscription;
import com.github.twitch4j.eventsub.Conduit;
import com.github.twitch4j.eventsub.EventSubSubscription;
import com.github.twitch4j.eventsub.events.StreamOnlineEvent;
import com.github.twitch4j.eventsub.socket.conduit.TwitchConduitSocketPool;
import com.github.twitch4j.eventsub.socket.events.EventSocketConnectionStateEvent;
import com.github.twitch4j.eventsub.subscriptions.SubscriptionTypes;
import com.github.twitch4j.helix.domain.EventSubSubscriptionList;
import com.github.twitch4j.helix.domain.User;
import com.github.twitch4j.helix.domain.Stream;
import com.github.twitch4j.helix.TwitchHelix;
import com.github.twitch4j.helix.TwitchHelixBuilder;
import com.safjnest.App;
import com.safjnest.core.cache.managers.GenericCache;
import com.safjnest.utils.SettingsLoader;
import com.safjnest.utils.log.BotLogger;

public class TwitchClient {
    private static String clientId;
    private static String clientSecret;

    private static TwitchHelix helix = null;
    private static TwitchConduitSocketPool conduit = null;

    private static GenericCache<String, User> streamersCache;

    private static String userUrl = "https://www.twitch.tv/{user}";
    private static String boxArtUrl = "https://static-cdn.jtvnw.net/ttv-boxart/{game}-{width}x{height}.jpg";
    private static String twitchIconUrl = "https://static-00.iconduck.com/assets.00/twitch-icon-512x512-ws2eyit3.png";

    static {
        
        TwitchClient.clientId = SettingsLoader.getSettings().getJsonSettings().getTwitch().getClientId();
        TwitchClient.clientSecret = SettingsLoader.getSettings().getJsonSettings().getTwitch().getClientSecret();

        streamersCache = new GenericCache<String, User>(100, 1, TimeUnit.MINUTES, String.class, User.class);
    }

    public static GenericCache<String, User> getStreamersCache() {
        return streamersCache;
    }

    public static TwitchConduitSocketPool getConduit() {
        return conduit;
    }

    public static void init() {
        if (App.isTesting()) return;
        BotLogger.trace("[TWITCH] Creating twitch client...");
        helix = TwitchHelixBuilder.builder()
            .withClientId(clientId)
            .withClientSecret(clientSecret)
            .build();

        Conduit firstConduit = getFirstConduit();
        updateConduit(firstConduit.getId(), 1);
        try {
            conduit = TwitchConduitSocketPool.create(spec -> {
                spec.clientId(clientId);
                spec.clientSecret(clientSecret);
                spec.poolShards(1);
                spec.helix(helix);

                if(firstConduit != null) {
                    spec.conduitId(firstConduit.getId());
                }
                
            });
        } catch (Exception e1) {
            BotLogger.error("[TWITCH] Error connecting the conduit to twitch");
            e1.printStackTrace();
        }
        conduit.getEventManager().onEvent(StreamOnlineEvent.class, TwitchEventsHandler::onStreamOnlineEvent);
        conduit.getEventManager().onEvent(EventSocketConnectionStateEvent.class, TwitchEventsHandler::onSocketConnectionStateEvent);
    }

    public static Conduit getFirstConduit() {
        List<Conduit> conduitList = helix.getConduits(null).execute().getConduits();
        return conduitList.size() > 0 ? conduitList.get(0) : null;
    }

    public static void updateConduit(String conduitId, int shardCount) {
        helix.updateConduit(null, conduitId, 1).execute();
    }

    public static void registerSubEvent(String streamerId) {        
        if(helix.getEventSubSubscriptions(null, null, null, streamerId, null, null).execute().getSubscriptions().size() > 0)
            return;
        
        BotLogger.trace("[TWITCH] Registering subscription for " + streamerId);
        conduit.register(SubscriptionTypes.STREAM_ONLINE, b -> b.broadcasterUserId(streamerId).build());
    }

    public static void unregisterSubEvent(String streamerId) {
        List<EventSubSubscription> subs = helix.getEventSubSubscriptions(null, null, null, streamerId, null, null).execute().getSubscriptions();
        if(subs.size() == 0)
            return;

        BotLogger.trace("[TWITCH] Unregistering subscription for " + streamerId);
        conduit.unregister(subs.get(0));
    }

    public static List<IEventSubscription> getRegisteredSubEvents() {
        return conduit.getEventManager().getActiveSubscriptions();
    }

    public static EventSubSubscriptionList getSubscriptionsList() {
        return helix.getEventSubSubscriptions(null, null, null, null, null, null).execute();
    }

    public static User getStreamerByName(String streamerName) {
        List<User> users = helix.getUsers(null, null, List.of(streamerName)).execute().getUsers();
        if (users.size() == 0) return null;

        User user = users.get(0);
        streamersCache.put(user.getId(), user);

        return user;
    }

    public static List<User> getStreamersById(List<String> streamerIds) {
        if (streamersCache.keySet().containsAll(streamerIds)) {
            return new ArrayList<>(streamersCache.get(streamerIds));
        }

        List<String> notCached = streamerIds.stream().filter(id -> !streamersCache.keySet().contains(id)).toList();
        List<User> users = helix.getUsers(null, notCached, null).execute().getUsers();
     
        for (User user : users) {
            streamersCache.put(user.getId(), user);
        }

        return new ArrayList<>(streamersCache.get(streamerIds));
    }

    public static User getStreamerById(String streamerId) {
        List<User> streamer = getStreamersById(List.of(streamerId));
        if(streamer.size() == 0) {
            return null;
        }
        return streamer.get(0);
    }

    public static Stream getStream(String streamerId) {
        List<Stream> streams = helix.getStreams(null, null, null, null, null, null, List.of(streamerId), null).execute().getStreams();
        if (streams.size() == 0) return null;

        return streams.get(0);
    }

    public static String getStreamerUrl(String streamerLogin) {
        return userUrl.replaceAll(Pattern.quote("{user}"), streamerLogin);
    }

    public static String getBoxArtUrl(String gameId, Integer width, Integer height) {
        return boxArtUrl.replaceAll(Pattern.quote("{game}"), gameId)
                        .replaceAll(Pattern.quote("{width}"), width.toString())
                        .replaceAll(Pattern.quote("{height}"), height.toString());
    }

    public static String getTwitchIconUrl() {
        return twitchIconUrl;
    }
    
}
