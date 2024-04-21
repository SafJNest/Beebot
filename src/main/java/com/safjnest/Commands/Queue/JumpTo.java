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
 */
public class JumpTo extends Command {

    public JumpTo() {
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

        if (event.getArgs().isEmpty()) {
            event.reply("Please provide a valid number");
            return;
        }
        TrackScheduler ts = PlayerManager.get().getGuildMusicManager(guild).getTrackScheduler();

        int position = Integer.parseInt(event.getArgs()) - 1;
        if (position > ts.getQueue().size()) {
            event.reply("There are only " + ts.getQueue().size() + " songs in the queue");
            return;
        }
        else if (position < 0) {
            event.reply("Please provide a valid number");
            return;
        }
        position--;
        ts.getPlayer().stopTrack();
        ts.play(ts.moveCursor(position - ts.getIndex()));
        
        QueueHandler.sendQueueEmbed(guild, event.getChannel());
    }
}