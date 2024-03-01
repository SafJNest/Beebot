package com.safjnest.Utilities.EventHandlers;

import com.safjnest.Utilities.Bot.Guild.GuildData;
import com.safjnest.Utilities.Bot.Guild.GuildSettings;
import com.safjnest.Utilities.Bot.Guild.Alert.AlertData;
import com.safjnest.Utilities.Bot.Guild.Alert.AlertType;
import com.safjnest.Utilities.EXPSystem.ExpSystem;
import com.safjnest.Utilities.SQL.DatabaseHandler;
import com.safjnest.Utilities.SQL.ResultRow;

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
        
        User newGuy = e.getAuthor();

        if (!guildData.isExpSystemEnabled())
            return;	

        if(!guildData.getExpSystemRoom(e.getChannel().getIdLong()))
            return;

        double modifier = guildData.getExpValueRoom(channel.getIdLong());
        int lvl = farm.receiveMessage(e.getAuthor().getId(), guild.getId(), modifier);

        if(lvl == ExpSystem.NOT_LEVELED_UP)
            return;

        ResultRow reward = DatabaseHandler.getReward(guild.getId(), lvl);
        if(!reward.emptyValues()){
            String message = reward.get("message_text");
            Role role = guild.getRoleById(reward.get("role_id"));
            message = message.replace("#user", newGuy.getAsMention());
            message = message.replace("#level", String.valueOf(lvl));
            message = message.replace("#role", role.getName());
            guild.addRoleToMember(UserSnowflake.fromId(newGuy.getId()), role).queue();
            channel.sendMessage(message).queue();
            return;
        }

        AlertData alert = guildData.getAlert(AlertType.LEVEL_UP);
        if (alert == null || !alert.isValid()) {
            channel.sendMessage("Congratulations, you are now level: " + lvl).queue();
            return;
        }

        String message = alert.getMessage();
        message = message.replace("#user", newGuy.getAsMention());
        message = message.replace("#level", String.valueOf(lvl));
        e.getChannel().asTextChannel().sendMessage(message).queue();
        return;
        
    }

    @Override
    public void onRoleDelete(RoleDeleteEvent event){
        DatabaseHandler.deleteReward(event.getRole().getId());
    }

    @Override
    public void onChannelDelete(ChannelDeleteEvent event){
        if(event.getChannelType().isAudio()){
            DatabaseHandler.deleteRoom(event.getGuild().getId(), event.getChannel().getId());
        }
    }


}