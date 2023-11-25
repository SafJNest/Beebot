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

public class Previous extends Command{
    
    public Previous() {
        this.name = this.getClass().getSimpleName().toLowerCase();
        this.aliases = new String[]{"pv", "lets go back dio cane"};
    }

    @Override
    protected void execute(CommandEvent event) {
        Guild guild = event.getGuild();

        PlayerManager pm = PlayerPool.contains(event.getSelfUser().getId(), guild.getId()) ? PlayerPool.get(event.getSelfUser().getId(), guild.getId()) : PlayerPool.createPlayer(event.getSelfUser().getId(), guild.getId());
        TrackScheduler ts = pm.getTrackScheduler();

        /* 
        if(ts.getStackSize() == 0) {
            event.reply("cant go back if there is no song dio cane svegliati dal coma");
            return;
        }
        */

        AudioTrack prevTrack = ts.prevTrack();
        if(prevTrack == null) {
            event.reply("cant go back if there is no song dio cane svegliati dal coma");
            return;
        }
        ts.playForce(prevTrack);
        event.reply("back into the past");


    }
}
