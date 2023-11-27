package com.safjnest.Commands.Audio;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.Utilities.Audio.PlayerManager;
import com.safjnest.Utilities.Audio.TrackScheduler;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;


public class Previous extends Command{
    
    public Previous() {
        this.name = this.getClass().getSimpleName().toLowerCase();
        this.aliases = new String[]{"pv", "lets go back dio cane"};
    }

    @Override
    protected void execute(CommandEvent event) {
        Guild guild = event.getGuild();
        User self = event.getSelfUser();
        TrackScheduler ts = PlayerManager.get().getGuildMusicManager(guild, self).getTrackScheduler();
        AudioTrack prevTrack = ts.prevTrack();
        
        if(prevTrack == null) {
            event.reply("This is the beginning of the queue");
            return;
        }

        ts.playForce(prevTrack);
        event.reply("Previous");
    }
}
