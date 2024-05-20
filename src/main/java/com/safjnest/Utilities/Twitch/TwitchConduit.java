package com.safjnest.Utilities.Twitch;

import java.util.List;

import com.github.philippheuer.events4j.api.domain.IEventSubscription;
import com.github.twitch4j.eventsub.events.StreamOnlineEvent;
import com.github.twitch4j.eventsub.socket.IEventSubConduit;
import com.github.twitch4j.eventsub.socket.conduit.TwitchConduitSocketPool;
import com.github.twitch4j.eventsub.subscriptions.SubscriptionTypes;

public class TwitchConduit {
    private static IEventSubConduit conduit = null;

    public TwitchConduit(String clientId, String clientSecret) {
        try {
            conduit = TwitchConduitSocketPool.create(spec -> {
                spec.clientId("***REMOVED***");
                spec.clientSecret("***REMOVED***");
                spec.poolShards(1);
            });

            conduit.getEventManager().onEvent(StreamOnlineEvent.class, TwitchEventsHandler::onStreamOnlineEvent);
            
        } catch (Exception e1) {
            System.out.println("[ERROR] Could not connect to twitch");
            e1.printStackTrace();
        }
    }

    public static void registerSubEvent(String streamerId) {
        conduit.register(SubscriptionTypes.STREAM_ONLINE, b -> b.broadcasterUserId(streamerId).build());
    }

    public static List<IEventSubscription> getRegisteredSubEvents() {
        return conduit.getEventManager().getActiveSubscriptions();
    }

}
