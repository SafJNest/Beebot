package com.safjnest.Utilities.EventHandlers;

import java.util.ArrayList;

import com.safjnest.Utilities.EXPSystem.ExpSystem;
import com.safjnest.Utilities.Guild.GuildData;
import com.safjnest.Utilities.Guild.GuildSettings;
import com.safjnest.Utilities.SQL.DatabaseHandler;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.channel.ChannelDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.role.RoleDeleteEvent;
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
    /**
     * The ExpSystem object that handles the exp system.
     */
    private ExpSystem farm;
    
    private GuildSettings settings;

    /**
     * Constructor for the TheListenerBeebot class.
     */
    public EventHandlerBeebot(GuildSettings settings, ExpSystem farm) {
        this.farm = farm;
        this.settings = settings;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent e) {
        if (e.getAuthor().isBot())
            return;
        
        GuildData guildData = settings.getServer(e.getGuild().getId());
        Guild guild = e.getGuild();
        TextChannel channel = e.getChannel().asTextChannel();

        if (!guildData.isExpSystemEnabled())
            return;	

        if(!guildData.getExpSystemRoom(e.getChannel().getIdLong()))
            return;

        double modifier = guildData.getExpValueRoom(channel.getIdLong());
        int lvl = farm.receiveMessage(e.getAuthor().getId(), guild.getId(), modifier);
        if(lvl == -1)
            return;

        User newGuy = e.getAuthor();

        String query = "SELECT role_id, message_text FROM rewards_table WHERE guild_id = '" + guild + "' AND level = '" + lvl + "';";
        ArrayList<String> arr = DatabaseHandler.getSql().getSpecifiedRow(query, 0);
        if(arr != null){
            String message = arr.get(1);
            Role role = guild.getRoleById(arr.get(0));
            message = message.replace("#user", newGuy.getAsMention());
            message = message.replace("#level", String.valueOf(lvl));
            message = message.replace("#role", role.getName());
            guild.addRoleToMember(UserSnowflake.fromId(newGuy.getId()), role).queue();
            channel.sendMessage(message).queue();
            return;
        }
        
        query = "SELECT message_text FROM levelup_message WHERE guild_id = '" + guildData.getId() + "';";
        arr = DatabaseHandler.getSql().getSpecifiedRow(query, 0);

        if (arr == null){
            channel.sendMessage("Congratulations, you are now level: " + lvl).queue();
            return;
        }

        String message = arr.get(0);
        message = message.replace("#user", newGuy.getAsMention());
        message = message.replace("#level", String.valueOf(lvl));
        e.getChannel().asTextChannel().sendMessage(message).queue();
        return;
        
    }

    @Override
    public void onRoleDelete(RoleDeleteEvent event){
        String query = "DELETE FROM rewards_table WHERE role_id = '" + event.getRole().getId() + "';";
        DatabaseHandler.getSql().runQuery(query);
    }

    @Override
    public void onChannelDelete(ChannelDeleteEvent event){
        if(event.getChannelType().isAudio()){
            String query = "DELETE from rooms_settings WHERE guild_id = '" + event.getGuild().getId()
                           + "' AND room_id = '" + event.getChannel().getId() + "';";
            DatabaseHandler.getSql().runQuery(query);
        }
    }


}