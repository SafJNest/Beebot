package com.safjnest.Commands.Queue;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.Utilities.CommandsLoader;
import com.safjnest.Utilities.Audio.PlayerManager;
import com.safjnest.Utilities.Audio.QueueHandler;
import com.safjnest.Utilities.Audio.TrackScheduler;

import net.dv8tion.jda.api.entities.Guild;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 1.0
 */
public class Pause extends Command {

    public Pause(){
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
        ts.getPlayer().setPaused(true);

        QueueHandler.sendEmbed(event);
    }
}