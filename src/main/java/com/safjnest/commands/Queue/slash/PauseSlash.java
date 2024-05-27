package com.safjnest.commands.Queue.slash;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.core.audio.PlayerManager;
import com.safjnest.core.audio.QueueHandler;
import com.safjnest.core.audio.TrackScheduler;
import com.safjnest.core.audio.types.ReplyType;
import com.safjnest.util.CommandsLoader;

import net.dv8tion.jda.api.entities.Guild;

public class PauseSlash extends SlashCommand {

    public PauseSlash(){
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
        
        ts.pause(true);

        QueueHandler.sendEmbed(event, ReplyType.REPLY);
    }
}
