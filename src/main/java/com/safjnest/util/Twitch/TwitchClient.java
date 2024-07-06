package com.safjnest.util.Twitch;

import java.util.List;
import java.util.regex.Pattern;

import com.github.philippheuer.events4j.api.domain.IEventSubscription;
import com.github.twitch4j.ITwitchClient;
import com.github.twitch4j.TwitchClientBuilder;
import com.github.twitch4j.eventsub.Conduit;
import com.github.twitch4j.eventsub.EventSubSubscription;
import com.github.twitch4j.eventsub.events.StreamOnlineEvent;
import com.github.twitch4j.eventsub.socket.conduit.TwitchConduitSocketPool;
import com.github.twitch4j.eventsub.socket.events.EventSocketConnectionStateEvent;
import com.github.twitch4j.eventsub.subscriptions.SubscriptionTypes;
import com.github.twitch4j.helix.domain.EventSubSubscriptionList;
import com.github.twitch4j.helix.domain.User;
import com.github.twitch4j.helix.domain.Stream;
import com.safjnest.core.CacheMap;
import com.safjnest.util.TimeConstant;
import com.safjnest.util.log.BotLogger;

public class TwitchClient {
    private static String clientId;
    private static String clientSecret;

    private static ITwitchClient client = null;
    private static TwitchConduitSocketPool conduit = null;

    private static CacheMap<String, User> streamersCache;

    private static String userUrl = "https://www.twitch.tv/{user}";
    private static String boxArtUrl = "https://static-cdn.jtvnw.net/ttv-boxart/{game}-{width}x{heigth}";
    private static String twitchIconUrl = "https://static-00.iconduck.com/assets.00/twitch-icon-512x512-ws2eyit3.png";

    public TwitchClient(String clientId, String clientSecret) {
        TwitchClient.clientId = clientId;
        TwitchClient.clientSecret = clientSecret;

        streamersCache = new CacheMap<>(TimeConstant.MINUTE, TimeConstant.MINUTE, 100);
    }

    public static CacheMap<String, User> getStreamersCache() {
        return streamersCache;
    }

    public static ITwitchClient getClient() {
        return client;
    }

    public static TwitchConduitSocketPool getConduit() {
        return conduit;
    }

    public static void init() {
        BotLogger.trace("[TWITCH] Creating twitch client...");
        client = TwitchClientBuilder.builder()
            .withEnableHelix(true)
            .withEnableEventSocket(true)
            .withClientId(clientId)
            .withClientSecret(clientSecret)
            .build();
        
        /*
        Conduit firstConduit = getFirstConduit();
        updateConduit(firstConduit.getId(), 1);
        try {
            conduit = TwitchConduitSocketPool.create(spec -> {
                spec.clientId(clientId);
                spec.clientSecret(clientSecret);
                spec.poolShards(1);
                spec.helix(client.getHelix());

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
        */
    }

    public static Conduit getFirstConduit() {
        List<Conduit> conduitList = client.getHelix().getConduits(null).execute().getConduits();
        return conduitList.size() > 0 ? conduitList.get(0) : null;
    }

    public static void updateConduit(String conduitId, int shardCount) {
        TwitchClient.getClient().getHelix().updateConduit(null, conduitId, 1).execute();
    }

    public static void registerSubEvent(String streamerId) {        
        if(client.getHelix().getEventSubSubscriptions(null, null, null, streamerId, null, null).execute().getSubscriptions().size() > 0) 
            return;
        
        BotLogger.trace("[TWITCH] Registering subscription for " + streamerId);
        conduit.register(SubscriptionTypes.STREAM_ONLINE, b -> b.broadcasterUserId(streamerId).build());
    }

    public static void unregisterSubEvent(String streamerId) {
        List<EventSubSubscription> subs = client.getHelix().getEventSubSubscriptions(null, null, null, streamerId, null, null).execute().getSubscriptions();
        if(subs.size() == 0)
            return;

        BotLogger.trace("[TWITCH] Unregistering subscription for " + streamerId);
        conduit.unregister(subs.get(0));
    }

    public static List<IEventSubscription> getRegisteredSubEvents() {
        return conduit.getEventManager().getActiveSubscriptions();
    }

    public static EventSubSubscriptionList getSubscriptionsList() {
        return client.getHelix().getEventSubSubscriptions(null, null, null, null, null, null).execute();
    }

    public static User getStreamerByName(String streamerName) {
        List<User> users = TwitchClient.getClient().getHelix().getUsers(null, null, List.of(streamerName)).execute().getUsers();
        if (users.size() == 0) return null;

        User user = users.get(0);
        streamersCache.put(user.getId(), user);

        return user;
    }

    public static List<User> getStreamersById(List<String> streamerIds) {
        if (streamersCache.keySet().containsAll(streamerIds)) {
            return streamersCache.get(streamerIds);
        }

        List<String> notCached = streamerIds.stream().filter(id -> !streamersCache.keySet().contains(id)).toList();
        List<User> users = TwitchClient.getClient().getHelix().getUsers(null, notCached, null).execute().getUsers();
     
        for (User user : users) {
            streamersCache.put(user.getId(), user);
        }

        return streamersCache.get(streamerIds);
    }

    public static User getStreamerById(String streamerId) {
        List<User> streamer = getStreamersById(List.of(streamerId));
        if(streamer.size() == 0) {
            return null;
        }
        return streamer.get(0);
    }

    public static Stream getStream(String streamerId) {
        List<Stream> streams = TwitchClient.getClient().getHelix().getStreams(null, null, null, null, null, null, List.of(streamerId), null).execute().getStreams();
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
