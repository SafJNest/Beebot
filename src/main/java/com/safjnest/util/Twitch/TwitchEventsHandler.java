package com.safjnest.util.Twitch;

import com.github.twitch4j.eventsub.events.StreamOnlineEvent;
import com.safjnest.core.Bot;
import com.safjnest.sql.DatabaseHandler;
import com.safjnest.sql.QueryResult;
import com.safjnest.sql.ResultRow;
import com.safjnest.util.PermissionHandler;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

class TwitchEventsHandler {

    public static void onStreamOnlineEvent(StreamOnlineEvent event) {
        QueryResult result = DatabaseHandler.getTwitchSubscriptions(event.getBroadcasterUserId());

        for (ResultRow guildRow : result) {
            Guild guild = Bot.getJDA().getGuildById(guildRow.get("guild_id"));
            TextChannel channel = guild.getTextChannelById(guildRow.get("channel_id"));
            Role role = guildRow.get("role_id") != null ? guild.getRoleById(guildRow.get("role_id")) : null;

            System.out.println("user login: " + event.getBroadcasterUserLogin());

            EmbedBuilder eb = new EmbedBuilder();

            //eb.setAuthor();
            eb.setTitle(PermissionHandler.escapeString(event.getBroadcasterUserName() + " is live on: https://www.twitch.tv/" + event.getBroadcasterUserLogin()));
            eb.setDescription("oid");
            //eb.setThumbnail(getThumbnail(ts.getPlayer().getPlayingTrack()));
            eb.setColor(Bot.getColor());

            channel.sendMessage(role != null ? role.getAsMention() : "").addEmbeds(eb.build()).queue();
            
        }

        System.out.printf("Channel is active! %s", event.getBroadcasterUserName());
    }

}