package com.safjnest.commands.Queue;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.core.audio.PlayerManager;
import com.safjnest.core.audio.QueueHandler;
import com.safjnest.core.audio.TrackScheduler;
import com.safjnest.util.CommandsLoader;
import com.safjnest.util.SafJNest;

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

        if (event.getArgs().isEmpty() || !SafJNest.intIsParsable(event.getArgs())) {
            event.reply("Please provide a valid number");
            return;
        }

        int position = Integer.parseInt(event.getArgs());
        TrackScheduler ts = PlayerManager.get().getGuildMusicManager(guild).getTrackScheduler();

        position--;
        ts.getPlayer().stopTrack();
        ts.play(ts.moveCursor(position - ts.getIndex()));
        
        QueueHandler.sendEmbed(event);
    }
}