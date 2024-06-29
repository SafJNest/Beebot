package com.safjnest.util.Twitch;

import java.util.List;

import com.github.philippheuer.events4j.api.domain.IEventSubscription;
import com.github.twitch4j.ITwitchClient;
import com.github.twitch4j.TwitchClientBuilder;
import com.github.twitch4j.eventsub.Conduit;
import com.github.twitch4j.eventsub.EventSubSubscription;
import com.github.twitch4j.eventsub.condition.ChannelEventSubCondition;
import com.github.twitch4j.eventsub.events.StreamOnlineEvent;
import com.github.twitch4j.eventsub.socket.conduit.TwitchConduitSocketPool;
import com.github.twitch4j.eventsub.subscriptions.SubscriptionTypes;
import com.github.twitch4j.helix.domain.EventSubSubscriptionList;
import com.github.twitch4j.helix.domain.User;
import com.github.twitch4j.helix.domain.Stream;
import com.safjnest.util.log.BotLogger;

public class TwitchClient {
    private static String clientId;
    private static String clientSecret;

    private static ITwitchClient client = null;
    private static TwitchConduitSocketPool conduit = null;

    public TwitchClient(String clientId, String clientSecret) {
        TwitchClient.clientId = clientId;
        TwitchClient.clientSecret = clientSecret;
    }

    public static ITwitchClient getClient() {
        return client;
    }

    public static TwitchConduitSocketPool getConduit() {
        return conduit;
    }

    public static void init() {
        BotLogger.trace("[TWITCH] Creating helix...");
        client = TwitchClientBuilder.builder()
            .withEnableHelix(true)
            .withClientId(clientId)
            .withClientSecret(clientSecret)
            .build();

        try {
            conduit = TwitchConduitSocketPool.create(spec -> {
                spec.clientId(clientId);
                spec.clientSecret(clientSecret);
                spec.poolShards(1);
                spec.helix(client.getHelix());

                List<Conduit> conduitList = client.getHelix().getConduits(null).execute().getConduits();
                if(conduitList.size() > 0) {
                    spec.conduitId(conduitList.get(0).getId());
                }
            });
        } catch (Exception e1) {
            BotLogger.error("[TWITCH] Could not connect to twitch");
            e1.printStackTrace();
        }
        conduit.getEventManager().onEvent(StreamOnlineEvent.class, TwitchEventsHandler::onStreamOnlineEvent);
    }

    public static void registerSubEvent(String streamerId) {
        for(EventSubSubscription sub : getSubscriptionsList().getSubscriptions()) {
            if (((ChannelEventSubCondition) sub.getCondition()).getBroadcasterUserId().equals(streamerId)) {
                return;
            }
        }
        BotLogger.trace("[TWITCH] Registering subscription for " + streamerId);
        conduit.register(SubscriptionTypes.STREAM_ONLINE, b -> b.broadcasterUserId(streamerId).build());
    }

    public static List<IEventSubscription> getRegisteredSubEvents() {
        return conduit.getEventManager().getActiveSubscriptions();
    }

    public static EventSubSubscriptionList getSubscriptionsList() {
        return client.getHelix().getEventSubSubscriptions(null, null, null, null, null, null).execute();
    }

    public static String getStreamerId(String streamerName) {
        User streamer = getStreamer(streamerName);
        if (streamer == null) return null;

        return streamer.getId();
    }

    public static User getStreamer(String streamerName) {
        List<User> users = TwitchClient.getClient().getHelix().getUsers(null, null, List.of(streamerName)).execute().getUsers();
        if (users.size() == 0) return null;

        return users.get(0);
    }

    public static Stream getStream(String streamId) {
        List<Stream> streams = TwitchClient.getClient().getHelix().getStreams(null, null, null, null, null, null, List.of(streamId), null).execute().getStreams();
        if (streams.size() == 0) return null;

        return streams.get(0);
    }

    public static List<User> getStreamers(List<String> streamerIds) {
        return TwitchClient.getClient().getHelix().getUsers(null, streamerIds, null).execute().getUsers();
    }

    public static void unregisterSubEvent(String streamerId) {
        EventSubSubscription toUnregiter = null;
        for(EventSubSubscription sub : getSubscriptionsList().getSubscriptions()) {
            if (((ChannelEventSubCondition) sub.getCondition()).getBroadcasterUserId().equals(streamerId)) {
                toUnregiter = sub;
                break;
            }
        }
        if (toUnregiter == null) return;
        BotLogger.trace("[TWITCH] Unregistering subscription for " + streamerId);
        conduit.unregister(toUnregiter);
    }

}
