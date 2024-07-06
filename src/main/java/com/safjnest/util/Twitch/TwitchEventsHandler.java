package com.safjnest.util.Twitch;

import java.time.Instant;

import com.github.twitch4j.eventsub.events.StreamOnlineEvent;
import com.github.twitch4j.eventsub.socket.events.EventSocketConnectionStateEvent;
import com.github.twitch4j.helix.domain.User;
import com.safjnest.core.Bot;
import com.safjnest.sql.DatabaseHandler;
import com.safjnest.sql.QueryResult;
import com.safjnest.sql.ResultRow;
import com.safjnest.util.PermissionHandler;
import com.safjnest.util.log.BotLogger;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
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
            TextChannel channel = guild.getTextChannelById(guildRow.get("channel_id"));
            String message = guildRow.get("message") == null ? "" : guildRow.get("message");

            message = message.replace("#streamer", streamer.getDisplayName());

            channel.sendMessage(message).addEmbeds(eb.build()).queue();
        }
    }

    public static void onSocketConnectionStateEvent(EventSocketConnectionStateEvent event) {
        BotLogger.trace("[TWITCH] Socket connection state changed: " + event.getState().name());        
    }
}