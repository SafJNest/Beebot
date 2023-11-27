package com.safjnest.Commands.Audio;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;

import com.safjnest.Utilities.Audio.PlayerManager;
import com.safjnest.Utilities.Audio.TrackScheduler;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

public class Skip extends Command{
    
    public Skip() {
        this.name = this.getClass().getSimpleName().toLowerCase();
        this.aliases = new String[]{"skipper", "next"};
    }

    @Override
    protected void execute(CommandEvent event) {
        Guild guild = event.getGuild();
        User self = event.getSelfUser();
        TrackScheduler ts = PlayerManager.get().getGuildMusicManager(guild, self).getTrackScheduler();
        AudioTrack nextTrack = ts.nextTrack();
        
        if(nextTrack == null) {
            event.reply("This is the end of the queue");
            return;
        }

        ts.playForce(nextTrack);
        event.reply("Skipped");
    }
}
