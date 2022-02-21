package com.safjnest.Utilities;

import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * 
 * @since 1.2
 */
public class TheListener extends ListenerAdapter{

    @Override
    public void onGuildVoiceUpdate(GuildVoiceUpdateEvent e){
        if(e.getGuild().getAudioManager().isConnected() && e.getChannelLeft().getMembers().size() == 1 ){
            e.getGuild().getAudioManager().closeAudioConnection();
        }
    }
}