package com.safjnest.core.chat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.safjnest.util.lol.LeagueHandler;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.interactions.InteractionHook;

public class OmegleChannel {
    @SuppressWarnings("unused")
    private static final int inactivityBeforeStoppingAutoreconnect = 2;
    private static final List<String> staticNamePool = List.of(LeagueHandler.getChampions());
    
    private String channel;
    private String connectedChannel;
    private Webhook webhook;
    private Message message;
    private boolean autoReconnect;
    private boolean anonymous;
    private List<String> interests;
    private InteractionHook hook;
    private Map<String, String> anonymousNames; // user id -> unique name
    private List<String> namePool;

    public OmegleChannel(String channel, boolean autoReconnect, boolean anonymous) {
        this.channel = channel;
        this.connectedChannel = null;
        this.webhook = null;
        this.message = null;
        this.autoReconnect = autoReconnect;
        this.anonymous = anonymous;
        this.hook = null;

        this.anonymousNames = new HashMap<>(); 
        this.namePool = new ArrayList<>(staticNamePool);
    }

    public OmegleChannel(String channel, boolean autoReconnect, boolean anonymous, InteractionHook hook) {
        this.channel = channel;
        this.connectedChannel = null;
        this.webhook = null;
        this.message = null;
        this.autoReconnect = autoReconnect;
        this.anonymous = anonymous;
        this.hook = hook;

        this.anonymousNames = new HashMap<>(); 
        this.namePool = new ArrayList<>(staticNamePool);
    }

    public OmegleChannel(String channel, String connectedChannel, Webhook webhook, Message message, boolean autoReconnect, boolean anonymous) {
        this.channel = channel;
        this.connectedChannel = connectedChannel;
        this.webhook = webhook;
        this.message = message;
        this.autoReconnect = autoReconnect;
        this.anonymous = anonymous;

        this.anonymousNames = new HashMap<>(); 
        this.namePool = new ArrayList<>(staticNamePool);
    }

    public OmegleChannel(String channel, String connectedChannel, InteractionHook hook, Webhook webhook, Message message, boolean autoReconnect, boolean anonymous, List<String> interests) {
        this.channel = channel;
        this.connectedChannel = connectedChannel;
        this.hook = hook;
        this.webhook = webhook;
        this.message = message;
        this.autoReconnect = autoReconnect;
        this.anonymous = anonymous;
        this.interests = interests;

        this.anonymousNames = new HashMap<>(); 
        this.namePool = new ArrayList<>(staticNamePool);
    }


    public String getAnonymousName(String userId) {
        if (!anonymousNames.containsKey(userId)) {
            String name;

            if(namePool.isEmpty()) namePool = new ArrayList<>(staticNamePool);

            if (userId.equals("383358222972616705")){
                name = "Thresh"; //sanek otp champ
                namePool.remove(name);
            } 
            else if(userId.equals("440489230968553472")) {
                name = "Samira"; //leon broken champ abuser :(
                namePool.remove(name);
            }
            else {
                name = namePool.remove((int) (Math.random() * namePool.size()));
            }
            anonymousNames.put(userId, name);
        }

        return anonymousNames.get(userId);
    }

    public String getChannel() {
        return channel;
    }

    public String getConnectedChannel() {
        return connectedChannel;
    }

    public Webhook getWebhook() {
        return webhook;
    }

    public Message getMessage() {
        return message;
    }

    public boolean isAutoReconnect() {
        return autoReconnect;
    }

    public boolean isAnonymous() {
        return anonymous;
    }

    public List<String> getInterests() {
        return interests;
    }

    public InteractionHook getHook() {
        return hook;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public void setConnectedChannel(String connectedChannel) {
        this.connectedChannel = connectedChannel;
    }

    public void setWebhook(Webhook webhook) {
        this.webhook = webhook;
    }

    public void setMessage(Message message) {
        this.message = message;
    }

    public void setAutoReconnect(boolean autoReconnect) {
        this.autoReconnect = autoReconnect;
    }

    public void setAnonymous(boolean anonymous) {
        this.anonymous = anonymous;
    }

    public void setInterests(List<String> interests) {
        this.interests = interests;
    }

    public void setInteraction(InteractionHook interaction) {
        this.hook = interaction;
    }


}
