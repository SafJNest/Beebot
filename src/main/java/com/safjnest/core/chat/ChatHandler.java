package com.safjnest.core.chat;

import java.net.URL;
import java.util.*;

import com.safjnest.commands.misc.Help;
import com.safjnest.core.Bot;
import com.safjnest.util.CommandsLoader;
import com.safjnest.util.TimeConstant;
import com.safjnest.util.lol.LeagueHandler;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Icon;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.LayoutComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import rx.internal.util.SynchronizedQueue;

public class ChatHandler {
    private static final long connectTimeoutDelay = TimeConstant.SECOND * 10;
    private static final long disconnectTimeoutDelay = TimeConstant.SECOND * 10;;

    private static final Map<String, Set<String>> channelGroups = new HashMap<>();
    private static final Map<String, OmegleChannel> omegleChannels = new HashMap<>();
    private static final Queue<TextChannel> waitingRoom = new SynchronizedQueue<TextChannel>();
    private static final Map<String, Timer> connectTimers = new HashMap<>();
    private static final Map<String, Timer> disconnectTimers = new HashMap<>();
    
    public static List<LayoutComponent> getRequesstButtons(String channelID) {
        List<LayoutComponent> buttons = new ArrayList<>();
        buttons.add(ActionRow.of(
            Button.danger("chat-refuse-" + channelID, "refuse"),
            Button.success("chat-accept-" + channelID, "accept")
        ));
        return buttons;
    }

    public static String showConnections() {
        return channelGroups.toString();
    }

    public static void sendWaitingEmbed(GuildMessageChannel channel) {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("Waiting for someone to join...");
        eb.setColor(Bot.getColor());
        eb.setDescription("You are currently in the waiting room. Please wait for someone to join.");
        if (omegleChannels.get(channel.getId()).getHook() == null) {
            Message msg = channel.sendMessageEmbeds(eb.build()).complete();
            omegleChannels.get(channel.getId()).setMessage(msg);
            return;
        }
        
        omegleChannels.get(channel.getId()).getHook().editOriginalEmbeds(eb.build()).queue();
    }


    public static void sendConnectedEmbed(TextChannel channel, TextChannel otherChannel) {        
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("Connected!");
        eb.setColor(Bot.getColor());
        eb.setDescription("You are now talking with one or more strangers.");

        if (omegleChannels.get(channel.getId()).getHook() != null) 
            omegleChannels.get(channel.getId()).getHook().editOriginalEmbeds(eb.build()).queue();
        else
            channel.sendMessageEmbeds(eb.build()).queue();
    }
    
    public static void addConnection(String... channels) {
        for (String channel : channels) {
            channelGroups.putIfAbsent(channel, new HashSet<>());
            Set<String> set = channelGroups.get(channel);
            for(String c : channels) {
                set.add(c);
            }
            set.remove(channel);
        }
    }

    public static void sendRequest(GuildMessageChannel requester, List<String> channels) {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setAuthor(requester.getGuild().getName(), requester.getJumpUrl(), requester.getGuild().getIconUrl());
        eb.setTitle("Channel connection request");
        eb.setDescription(requester.getGuild().getName() + " wants to connect they're `" + requester.getName() + "` channel to this.\nDo you accept?");
        for(String channel : channels) {
            Bot.getJDA().getTextChannelById(channel)
                    .sendMessageEmbeds(eb.build()).setComponents(getRequesstButtons(requester.getId()))
                    .queue();
        }
    }

    public static void removeConnection(String... channels) {
        for(String channel : channels) {
            channelGroups.get(channel).removeAll(List.of(channels));
        }
    }

    public static void removeAllConnections(String channel) {
        channelGroups.get(channel).clear();
        for(Set<String> set : channelGroups.values()) {
            set.remove(channel);
        }
    }

    public static void omegle(TextChannel channel, boolean autoReconnect, boolean anonymous, InteractionHook hook) {
        TextChannel otherChannel = waitingRoom.peek();
        omegleChannels.putIfAbsent(channel.getId(), new OmegleChannel(channel.getId(), autoReconnect, anonymous, hook));

        if(waitingRoom.contains(channel)) {
            if(hook == null) {
                channel.sendMessage("You are already waiting!").queue();
            }
            else {
                hook.editOriginal("You are already waiting!").queue();
            }
            return;
        }

        if(omegleChannels.get(channel.getId()).getConnectedChannel() != null) {
            if(hook == null) {
                channel.sendMessage("You are already connected with another channel!").queue();
            }
            else {
                hook.editOriginal("You are already connected with another channel!").queue();
            }
            return;
        }

        if(otherChannel == null) {
            waitingRoom.add(channel);
            makeConnectTimer(channel);
            sendWaitingEmbed(channel);
            return;
        }

        waitingRoom.poll();

        if (omegleChannels.get(otherChannel.getId()).getMessage() != null)
            omegleChannels.get(otherChannel.getId()).getMessage().delete().queue();

        omegleConnect(channel, otherChannel);
    }

    public static void omegleConnect(TextChannel channel, TextChannel otherChannel) {
        OmegleChannel omegleChannel = omegleChannels.get(channel.getId());
        OmegleChannel otherOmegleChannel = omegleChannels.get(otherChannel.getId());
        try {
            if(omegleChannel.getWebhook() == null) {
                //TODO forse vedere se il webook esiste nella guild
                omegleChannel.setWebhook(channel.createWebhook(otherChannel.getGuild().getName()).complete());
            }
            if(otherOmegleChannel.getWebhook() == null) {
                otherOmegleChannel.setWebhook(otherChannel.createWebhook(channel.getGuild().getName()).complete());
            }
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        System.out.println("timer cancellato");
        connectTimers.get(otherChannel.getId()).cancel();
        connectTimers.remove(otherChannel.getId());

        makeDisconnectTimer(channel, otherChannel);

        sendConnectedEmbed(channel, otherChannel);
        sendConnectedEmbed(otherChannel, channel);
        omegleChannel.setConnectedChannel(otherChannel.getId());
        otherOmegleChannel.setConnectedChannel(channel.getId());
    }

    public static void omegleDisconnect(String channel, InteractionHook hook) {
        if(!omegleChannels.containsKey(channel))
            return;

        OmegleChannel omegle = omegleChannels.remove(channel);
        OmegleChannel otherOmegle = omegleChannels.remove(omegle.getConnectedChannel());

        omegle.getWebhook().delete().queue();
        otherOmegle.getWebhook().delete().queue();

        disconnectTimers.remove(channel).cancel();
        disconnectTimers.remove(omegle.getConnectedChannel());
        
        sendDisconnectEmbed(channel);
        sendDisconnectEmbed(omegle.getConnectedChannel());
    }

    public static void sendDisconnectEmbed(String channel) {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("Disconnected!");
        eb.setColor(Bot.getColor());
        eb.setDescription("You have been disconnected from the chat.");
        Bot.getJDA().getTextChannelById(channel).sendMessageEmbeds(eb.build()).queue();
    }
    
    public static void relayMessage(MessageReceivedEvent e) {
        String channelId = e.getChannel().getId();
        
        /**god of if */
        if (e.getMessage().getContentRaw().startsWith(Bot.getGuildData(e.getGuild()).getPrefix()) && Help.searchCommand(e.getMessage().getContentRaw().split(" ", 2)[0].substring(Bot.getGuildData(e.getGuild()).getPrefix().length()), CommandsLoader.getCommandsData(e.getMember().getId())) != null)
            return;
        
        if(channelGroups.containsKey(channelId)) {
            Set<String> connectedChannels = channelGroups.get(channelId);

            for(String connectedChannelId : connectedChannels) {
                e.getJDA().getTextChannelById(connectedChannelId)
                        .sendMessage("**" + e.getAuthor().getName() + ":** " + e.getMessage().getContentRaw())
                        .queue();
            }
        }
        if(omegleChannels.containsKey(channelId) && omegleChannels.get(channelId).getConnectedChannel() != null) {
            OmegleChannel thisOmegle = omegleChannels.get(channelId);
            OmegleChannel otherOmegle = omegleChannels.get(thisOmegle.getConnectedChannel());
            try {
                if (thisOmegle.isAnonymous()) {
                    String name = thisOmegle.getAnonymousName(e.getAuthor().getId());
                    if(!otherOmegle.getWebhook().getName().equals(name)) {
                        otherOmegle.getWebhook().getManager()
                            .setName(name)
                            .setAvatar(Icon.from(new URL(LeagueHandler.getChampionProfilePic(name)).openStream()))
                        .complete();
                    }
                }
                else if (!otherOmegle.getWebhook().getName().equals(e.getAuthor().getName())){
                    otherOmegle.getWebhook().getManager()
                        .setName(e.getAuthor().getName())
                        .setAvatar(Icon.from(e.getAuthor().getAvatar().download().get()))
                    .complete();
                }
            } catch (Exception e1) {
                e1.printStackTrace();
            }
            otherOmegle.getWebhook().sendMessage(e.getMessage().getContentRaw()).queue();
            makeDisconnectTimer(e.getJDA().getTextChannelById(thisOmegle.getChannel()), e.getJDA().getTextChannelById(otherOmegle.getChannel()));
        }
    }

    private static void makeConnectTimer(TextChannel channel) {
        TimerTask connectTimeout = new TimerTask() {
            public void run() {
                System.out.println("time out");
                
                EmbedBuilder eb = new EmbedBuilder();
                eb.setTitle("You got timed out.");
                eb.setColor(Bot.getColor());
                eb.setDescription("To avoid waiting forever you got timed out, you are welcome to try again.");

                if (omegleChannels.get(channel.getId()).getMessage() != null)
                    omegleChannels.get(channel.getId()).getMessage().delete().queue();

                if (omegleChannels.get(channel.getId()).getHook() != null)
                    omegleChannels.get(channel.getId()).getHook().editOriginalEmbeds(eb.build()).queue();
                else
                    channel.sendMessageEmbeds(eb.build()).queue();
                waitingRoom.remove(channel);
            }
        };
        Timer timer = new Timer(channel.getId() + "connectTimeoutTimer");
        connectTimers.put(channel.getId(), timer);
        timer.schedule(connectTimeout, connectTimeoutDelay);
    }

    private static void sendDisconnectTimerEmbed(TextChannel channel) {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("You got timed out.");
        eb.setColor(Bot.getColor());
        eb.setDescription("for lack of activity from either channel you got disconnected. hehehehehe garbage piece of shit");

        channel.sendMessageEmbeds(eb.build()).queue();
    }

    private static void makeDisconnectTimer(TextChannel channel, TextChannel otherChannel) {
        TimerTask disconnectTimeout = new TimerTask() {
            public void run() {                
                sendDisconnectTimerEmbed(channel);
                sendDisconnectTimerEmbed(otherChannel);
                
                if(!omegleChannels.containsKey(channel.getId()))
                    return;
                
                OmegleChannel omegle = omegleChannels.remove(channel.getId());
                OmegleChannel otherOmegle = omegleChannels.remove(omegle.getConnectedChannel());
        
                omegle.getWebhook().delete().queue();
                otherOmegle.getWebhook().delete().queue();
        
                disconnectTimers.remove(channel.getId());
                disconnectTimers.remove(omegle.getConnectedChannel());
            }
        };

        if(disconnectTimers.get(channel.getId()) != null) {
           disconnectTimers.get(channel.getId()).cancel();
        }

        Timer timer = new Timer(channel.getId() + "-" + otherChannel.getId() + "disconnectTimeoutTimer");

        disconnectTimers.put(channel.getId(), timer);
        disconnectTimers.put(otherChannel.getId(), timer);
        timer.schedule(disconnectTimeout, disconnectTimeoutDelay);
    }
}
