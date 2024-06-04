package com.safjnest.util.Twitch;

import java.util.List;

import com.github.philippheuer.events4j.api.domain.IEventSubscription;
import com.github.twitch4j.ITwitchClient;
import com.github.twitch4j.TwitchClientBuilder;
import com.github.twitch4j.eventsub.EventSubSubscription;
import com.github.twitch4j.eventsub.condition.ChannelEventSubCondition;
import com.github.twitch4j.eventsub.events.StreamOnlineEvent;
import com.github.twitch4j.eventsub.socket.IEventSubConduit;
import com.github.twitch4j.eventsub.socket.conduit.TwitchConduitSocketPool;
import com.github.twitch4j.eventsub.subscriptions.SubscriptionTypes;
import com.github.twitch4j.helix.domain.ConduitList;
import com.github.twitch4j.helix.domain.EventSubSubscriptionList;
import com.safjnest.util.log.BotLogger;

public class TwitchClient {
    private static String clientId;
    private static String clientSecret;

    private static ITwitchClient client = null;
    private static IEventSubConduit conduit = null;

    public TwitchClient(String clientId, String clientSecret) {
        TwitchClient.clientId = clientId;
        TwitchClient.clientSecret = clientSecret;
    }

    public static IEventSubConduit getConduit() {
        return conduit;
    }

    public static void retrieveConduit(String conduitId) {
        try {
            conduit = TwitchConduitSocketPool.create(spec -> {
                spec.clientId(clientId);
                spec.clientSecret(clientSecret);
                spec.poolShards(1);
                spec.conduitId(conduitId);
            });

            conduit.getEventManager().onEvent(StreamOnlineEvent.class, TwitchEventsHandler::onStreamOnlineEvent);
            
        } catch (Exception e1) {
            BotLogger.error("[TWITCH] Could not connect to twitch");
            e1.printStackTrace();
        }
    }

    public static void createConduit() {
        BotLogger.trace("[TWITCH] Creating conduit...");
        try {
            conduit = TwitchConduitSocketPool.create(spec -> {
                spec.clientId(clientId);
                spec.clientSecret(clientSecret);
                spec.poolShards(1);
            });

            conduit.getEventManager().onEvent(StreamOnlineEvent.class, TwitchEventsHandler::onStreamOnlineEvent);
            
        } catch (Exception e1) {
            BotLogger.error("[TWITCH] Could not connect to twitch");
            e1.printStackTrace();
        }
    }

    public static void init() {
        BotLogger.trace("[TWITCH] Creating helix...");
        client = TwitchClientBuilder.builder()
            .withEnableHelix(true)
            .withClientId(clientId)
            .withClientSecret(clientSecret)
            .build();

        ConduitList conduits = client.getHelix().getConduits(null).execute();
        
        if(conduits.getConduits().size() == 0)
            createConduit();
        else 
            retrieveConduit(conduits.getConduits().get(0).getId());
    }

    public static void registerSubEvent(String streamerId) {
        boolean isAlreadySubbed = false;
        for(EventSubSubscription sub : getSubs().getSubscriptions()) {
            if (((ChannelEventSubCondition) sub.getCondition()).getBroadcasterUserId().equals(streamerId)) {
                isAlreadySubbed = true;
                break;
            }
        }

        if(!isAlreadySubbed)
            conduit.register(SubscriptionTypes.STREAM_ONLINE, b -> b.broadcasterUserId(streamerId).build());
    }

    public static List<IEventSubscription> getRegisteredSubEvents() {
        return conduit.getEventManager().getActiveSubscriptions();
    }

    public static EventSubSubscriptionList getSubs() {
        return client.getHelix().getEventSubSubscriptions(null, null, null, null, null, null).execute();
    }

}
