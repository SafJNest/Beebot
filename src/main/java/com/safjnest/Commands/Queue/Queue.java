package com.safjnest.Commands.Queue;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.Utilities.CommandsLoader;
import com.safjnest.Utilities.Audio.PlayerManager;
import com.safjnest.Utilities.Audio.QueueHandler;
import com.safjnest.Utilities.Audio.TrackScheduler;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import net.dv8tion.jda.api.entities.Guild;

import java.util.LinkedList;

public class Queue extends Command{
    
    public Queue() {
        this.name = this.getClass().getSimpleName().toLowerCase();
        this.aliases = new CommandsLoader().getArray(this.name, "alias");
        this.help = new CommandsLoader().getString(this.name, "help");
        this.cooldown = new CommandsLoader().getCooldown(this.name);
        this.category = new Category(new CommandsLoader().getString(this.name, "category"));
        this.arguments = new CommandsLoader().getString(this.name, "arguments");
    }

    @Override
    protected void execute(CommandEvent event) {
        Guild guild = event.getGuild();

        TrackScheduler ts = PlayerManager.get().getGuildMusicManager(guild).getTrackScheduler();
        LinkedList<AudioTrack> queue = ts.getQueue();

        if(queue.isEmpty()) {
            event.reply("```Queue is empty```");
            return;
        }

        QueueHandler.sendQueueEmbed(guild, event.getChannel());
    }
}
