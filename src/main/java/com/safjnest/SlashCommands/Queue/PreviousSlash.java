package com.safjnest.SlashCommands.Queue;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.Utilities.CommandsLoader;
import com.safjnest.Utilities.Audio.PlayerManager;
import com.safjnest.Utilities.Audio.QueueHandler;
import com.safjnest.Utilities.Audio.TrackScheduler;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.entities.Guild;

public class PreviousSlash extends SlashCommand{
    
    public PreviousSlash() {
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
    
        AudioTrack prevTrack = ts.getPrev();

        if(prevTrack == null) {
            event.deferReply(true).addContent("This is the beginning of the queue").queue();
            return;
        }

        ts.play(prevTrack, true);
        
        QueueHandler.sendEmbed(event, false);
    }
}
