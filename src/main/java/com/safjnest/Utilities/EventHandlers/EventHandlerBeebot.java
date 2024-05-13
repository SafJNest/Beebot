package com.safjnest.Utilities.EventHandlers;

import com.safjnest.Bot;
import com.safjnest.Utilities.UserData;
import com.safjnest.Utilities.Guild.GuildData;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/**
 * This class handles a few events that are only used by Beebot. These events:
 * <ul>
 * <li>On Message Received (for gaining exp)</li>
 * </ul>
 * 
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * 
 * @since 2.1
 */
public class EventHandlerBeebot extends ListenerAdapter {

    @Override
    public void onMessageReceived(MessageReceivedEvent e) {
        if (e.getAuthor().isBot())
            return;

        GuildData guildData = Bot.getGuildData(e.getGuild().getId());
        UserData userData = Bot.getUserData(e.getAuthor().getId());
        
        Functions.handleAlias(guildData, userData, e);
        Functions.handleExperience(guildData, e);
    }

}