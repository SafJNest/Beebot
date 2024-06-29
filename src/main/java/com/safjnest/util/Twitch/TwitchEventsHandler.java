package com.safjnest.util.Twitch;

import com.github.twitch4j.eventsub.events.StreamOnlineEvent;
import com.github.twitch4j.helix.domain.User;
import com.safjnest.core.Bot;
import com.safjnest.sql.DatabaseHandler;
import com.safjnest.sql.QueryResult;
import com.safjnest.sql.ResultRow;
import com.safjnest.util.log.BotLogger;

import java.util.List;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import com.github.twitch4j.helix.domain.Stream;

class TwitchEventsHandler {

    public static void onStreamOnlineEvent(StreamOnlineEvent event) {
        BotLogger.trace(event.getBroadcasterUserName() + " is now live on Twitch!");
        QueryResult result = DatabaseHandler.getTwitchSubscriptions(event.getBroadcasterUserId());
        User streamer = TwitchClient.getStreamer(event.getBroadcasterUserLogin());
        Stream stream = TwitchClient.getStream(event.getBroadcasterUserId());

        String liveUrl = "https://www.twitch.tv/" + streamer.getLogin();
        System.out.println("https://static-cdn.jtvnw.net/ttv-boxart/" + stream.getGameId() + "-285x380.jpg");
        for (ResultRow guildRow : result) {
            Guild guild = Bot.getJDA().getGuildById(guildRow.get("guild_id"));
            TextChannel channel = guild.getTextChannelById(guildRow.get("channel_id"));
            Role role = guildRow.get("role_id") != null ? guild.getRoleById(guildRow.get("role_id")) : null;

            EmbedBuilder eb = new EmbedBuilder();

            eb.setTitle(stream.getTitle(), liveUrl);
            eb.setThumbnail("https://static-cdn.jtvnw.net/ttv-boxart/" + stream.getGameId() + "-285x380.jpg");
            eb.setAuthor(streamer.getDisplayName() + " is now live on Twitch!", liveUrl, streamer.getProfileImageUrl());
            eb.addField("Game", stream.getGameName(), true);
            eb.setColor(Bot.getColor());

            eb.setImage(stream.getThumbnailUrl(400, 225));

            channel.sendMessage(role != null ? role.getAsMention() : "").addEmbeds(eb.build()).queue();
        }
    }
}