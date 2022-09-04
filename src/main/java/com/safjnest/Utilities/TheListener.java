package com.safjnest.Utilities;

import java.util.ArrayList;

import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateBoostTimeEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/**
 * This class handles all events that could occur during the listening:
 * <ul>
 * <li>On update of a voice channel (to make the bot leave an empty voice channel)</li>
 * <li>On join of a user (to make the bot welcome the new member)</li>
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * 
 * @since 1.2
 */
public class TheListener extends ListenerAdapter{
    private PostgreSQL sql;

    public TheListener(PostgreSQL sql){
        this.sql = sql;
    }
    /**
     * On update of a voice channel (to make the bot leave an empty voice channel)
     */
    @Override
    public void onGuildVoiceUpdate(GuildVoiceUpdateEvent e){
        if(e.getGuild().getAudioManager().isConnected() && e.getChannelLeft()!=null && e.getChannelLeft().getMembers().size() == 1 ){
            e.getGuild().getAudioManager().closeAudioConnection();
        }
    }

    /**
     * On join of a user (to make the bot welcome the new member)
     */
    @Override 
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        System.out.println("wehuyg8f");
        MessageChannel channel = null;
        User newGuy = event.getUser();
        String notNullPls = sql.getWelcomeChannel(event.getGuild().getId());
        System.out.println(notNullPls);
        if(notNullPls == null)
            return;
        channel = event.getGuild().getTextChannelById(notNullPls);
        String message = sql.getWelcomeMessage(event.getGuild().getId());
        message = message.replace("#user", newGuy.getAsMention());
        channel.sendMessage(message).queue();
        ArrayList<String> roles = sql.getWelcomeRoles(event.getGuild().getId());
        if(roles.size() > 0){
            for(String role : roles){
                event.getGuild().addRoleToMember(newGuy, event.getGuild().getRoleById(role)).queue();
            }
        }
    }

    /**
     * On update of a user's boost time (to make the bot praise the user)
     */    
    public void onGuildMemberUpdateBoostTime​(GuildMemberUpdateBoostTimeEvent event){
        User newguy = event.getUser();
        TextChannel welcome = event.getGuild().getSystemChannel();
        welcome.sendMessage("NO FUCKING WAY " + newguy.getAsMention() + " HA BOOSTATO IL SERVER!!\n" + event.getGuild().getBoostCount()).queue();
    }
}