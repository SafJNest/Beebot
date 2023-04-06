package com.safjnest.Utilities.Listeners;

import java.util.ArrayList;

import com.safjnest.Utilities.DatabaseHandler;
import com.safjnest.Utilities.EXPSystem.ExpSystem;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/**
 * This class handles all events that could occur during the listening:
 * <ul>
 * <li>On update of a voice channel (to make the bot leave an empty voice
 * channel)</li>
 * <li>On join of a user (to make the bot welcome the new member)</li>
 * 
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * 
 * @since 1.2
 */
public class TheListenerBeebot extends ListenerAdapter {
    private ExpSystem farm;

    public TheListenerBeebot() {
        farm = new ExpSystem();
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent e) {
        if (e.getAuthor().isBot())
            return;

        int lvl = farm.receiveMessage(e.getAuthor().getId(), e.getGuild().getId());
        if (lvl != -1) {
            MessageChannel channel = null;
            User newGuy = e.getAuthor();
            String query = "SELECT channel_id, message_text FROM levelup_message WHERE discord_id = '" + e.getGuild().getId() + "';";
            ArrayList<String> arr = DatabaseHandler.getSql().getSpecifiedRow(query, 0);
            if (arr == null){
                e.getChannel().asTextChannel().sendMessage("Congratulations, you are now level: " + lvl).queue();
                return;
            }
            channel = e.getGuild().getTextChannelById(arr.get(0));
            String message = arr.get(1);
            message = message.replace("#user", newGuy.getAsMention());
            message = message.replace("#level", String.valueOf(lvl));
            channel.sendMessage(message).queue();
        }

    }

}