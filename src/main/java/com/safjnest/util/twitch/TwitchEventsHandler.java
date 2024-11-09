package com.safjnest.util.twitch;

import java.time.Instant;

import com.github.twitch4j.eventsub.events.StreamOnlineEvent;
import com.github.twitch4j.eventsub.socket.events.EventSocketConnectionStateEvent;
import com.github.twitch4j.helix.domain.User;
import com.safjnest.core.Bot;
import com.safjnest.model.guild.GuildData;
import com.safjnest.model.guild.alert.TwitchData;
import com.safjnest.sql.DatabaseHandler;
import com.safjnest.sql.QueryResult;
import com.safjnest.sql.ResultRow;
import com.safjnest.util.PermissionHandler;
import com.safjnest.util.log.BotLogger;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import com.github.twitch4j.helix.domain.Stream;

class TwitchEventsHandler {

    public static void onStreamOnlineEvent(StreamOnlineEvent event) {
        BotLogger.trace("[TWITCH] " + event.getBroadcasterUserName() + " is now live on Twitch!");
        
        QueryResult result = DatabaseHandler.getTwitchSubscriptions(event.getBroadcasterUserId());
        
        User streamer = TwitchClient.getStreamerByName(event.getBroadcasterUserLogin());

        Stream stream = TwitchClient.getStream(event.getBroadcasterUserId());
        if (stream == null) {
            try {Thread.sleep(10000); } 
            catch (InterruptedException e) { }
            
            stream = TwitchClient.getStream(event.getBroadcasterUserId());
            if (stream == null) {
                BotLogger.error("[TWITCH] Stream is null for " + event.getBroadcasterUserName());
                PermissionHandler.getUntouchables().forEach((id) -> Bot.getJDA().retrieveUserById(id).complete().openPrivateChannel().queue((privateChannel) -> privateChannel.sendMessage("dio ladro schifoso animale non va twitch leggi la console svegliati").queue()));
            }
        }

        String liveUrl = TwitchClient.getStreamerUrl(streamer.getLogin());

        EmbedBuilder eb = new EmbedBuilder();
        if (stream != null) {
            eb.setTitle(!stream.getTitle().isEmpty() ? stream.getTitle() : "No Title", liveUrl);
            eb.setThumbnail(TwitchClient.getBoxArtUrl(stream.getGameId(), 285, 380));
            eb.addField("Game", stream.getGameName(), true);
            eb.setImage(stream.getThumbnailUrl(400, 225));
        }
        
        eb.setAuthor(streamer.getDisplayName() + " is now live on Twitch!", liveUrl, streamer.getProfileImageUrl());
        eb.setColor(Bot.getColor());
        eb.setFooter("Twitch", TwitchClient.getTwitchIconUrl());
        eb.setTimestamp(Instant.now());

        for (ResultRow guildRow : result) {
            Guild guild = Bot.getJDA().getGuildById(guildRow.get("guild_id"));
            GuildData g = Bot.getGuildData(guild);

            TwitchData twitchData = g.getTwitchdata(streamer.getId());
            if (twitchData == null) {
                BotLogger.error("[TWITCH] TwitchData is null for " + streamer.getDisplayName() + " in " + guild.getName());
                continue;
            }
            
            TextChannel channel = guild.getTextChannelById(twitchData.getChannelId());

            String message = twitchData.getMessage();
            String privateMessage = twitchData.hasPrivateMessage() ? twitchData.getPrivateMessage() : message;

            message = message.replace("#streamer", streamer.getDisplayName());
            privateMessage = privateMessage.replace("#streamer", streamer.getDisplayName());

            Role role = !twitchData.getStreamerRole().isBlank() ? guild.getRoleById(twitchData.getStreamerRole()) : null;
            

            final String finalMessage = message;
            final String finalPrivateMessage = privateMessage;

            switch (twitchData.getSendType()) {
                case CHANNEL:
                    channel.sendMessage(finalMessage).queue();
                    break;
                case PRIVATE:
                    for (Member member : guild.getMembersWithRoles(role)) {
                        if (member.getUser().isBot()) continue;
                        try {
                            member.getUser().openPrivateChannel().queue(channelPrivate -> {
                                channelPrivate.sendMessage(finalPrivateMessage).addEmbeds(eb.build()).queue();
                            }); 
                        } catch (Exception e) { 
                            BotLogger.error("[TWITCH] Error sending private message to " + member.getUser().getAsTag() + " in " + guild.getName());
                        }
                        
                    }
                    break;
                case BOTH:
                    channel.sendMessage(finalMessage).queue();
                    for (Member member : guild.getMembersWithRoles(role)) {
                        if (member.getUser().isBot()) continue;
                        try {
                            member.getUser().openPrivateChannel().queue(channelPrivate -> {
                                channelPrivate.sendMessage(finalPrivateMessage).addEmbeds(eb.build()).queue();
                            }); 
                        } catch (Exception e) { 
                            BotLogger.error("[TWITCH] Error sending private message to " + member.getUser().getAsTag() + " in " + guild.getName());
                        }
                        
                    }
                    break;
            }
        }
    }

    public static void onSocketConnectionStateEvent(EventSocketConnectionStateEvent event) {
        BotLogger.trace("[TWITCH] Socket connection state changed: " + event.getState().name());        
    }
}