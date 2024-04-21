package com.safjnest.SlashCommands.Queue;

import java.util.LinkedList;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.Utilities.CommandsLoader;
import com.safjnest.Utilities.Audio.PlayerManager;
import com.safjnest.Utilities.Audio.QueueHandler;
import com.safjnest.Utilities.Audio.TrackScheduler;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;


import net.dv8tion.jda.api.entities.Guild;


public class QueueSlash extends SlashCommand{
    
    public QueueSlash() {
        this.name = this.getClass().getSimpleName().replace("Slash", "").toLowerCase();
        this.aliases = new CommandsLoader().getArray(this.name, "alias");
        this.help = new CommandsLoader().getString(this.name, "help");
        this.cooldown = new CommandsLoader().getCooldown(this.name);
        this.category = new Category(new CommandsLoader().getString(this.name, "category"));
        this.arguments = new CommandsLoader().getString(this.name, "arguments");
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        Guild guild = event.getGuild();

        TrackScheduler ts = PlayerManager.get().getGuildMusicManager(guild).getTrackScheduler();
        LinkedList<AudioTrack> queue = ts.getQueue();

        if(queue.isEmpty()) {
            event.reply("```Queue is empty```");
            return;
        }

        QueueHandler.sendQueueEmbed(event, guild);
    }
}
