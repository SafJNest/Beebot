package com.safjnest.commands.Queue;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.core.audio.PlayerManager;
import com.safjnest.core.audio.QueueHandler;
import com.safjnest.core.audio.TrackScheduler;
import com.safjnest.util.CommandsLoader;

import net.dv8tion.jda.api.entities.Guild;

public class Previous extends Command{
    
    public Previous() {
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

        ts.play(ts.getPrevious(), true);

        event.reply(QueueHandler.getPrevEmbed(guild, event.getMember()));
        
        QueueHandler.sendEmbed(event);
    }
}
