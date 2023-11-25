package com.safjnest.Commands.Audio;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.Utilities.Audio.PlayerManager;
import com.safjnest.Utilities.Audio.PlayerPool;
import com.safjnest.Utilities.Audio.TrackScheduler;
import com.safjnest.Utilities.Bot.BotSettingsHandler;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;

import java.awt.Color;

public class Skip extends Command{
    
    public Skip() {
        this.name = this.getClass().getSimpleName().toLowerCase();
        this.aliases = new String[]{"skipper", "next"};
    }

    @Override
    protected void execute(CommandEvent event) {
        Guild guild = event.getGuild();

        PlayerManager pm = PlayerPool.contains(event.getSelfUser().getId(), guild.getId()) ? PlayerPool.get(event.getSelfUser().getId(), guild.getId()) : PlayerPool.createPlayer(event.getSelfUser().getId(), guild.getId());
        TrackScheduler ts = pm.getTrackScheduler();

        AudioTrack nextTrack = ts.nextTrack();
        if(nextTrack == null) {
            event.reply("cant go next if there is no song dio cane svegliati dal come");
            return;
        }
        ts.playForce(nextTrack);
        event.reply("Skipped to guma song");


    }
}
