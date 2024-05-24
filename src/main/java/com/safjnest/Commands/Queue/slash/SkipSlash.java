package com.safjnest.commands.Queue.slash;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.core.audio.PlayerManager;
import com.safjnest.core.audio.QueueHandler;
import com.safjnest.core.audio.TrackScheduler;
import com.safjnest.core.audio.types.ReplyType;
import com.safjnest.util.CommandsLoader;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.entities.Guild;

public class SkipSlash extends SlashCommand{
    
    public SkipSlash() {
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
        AudioTrack nextTrack = ts.moveCursor(1);
        
        ts.play(nextTrack, true);

        ReplyType replyType;

        if(nextTrack != null) {
            event.deferReply().addEmbeds(QueueHandler.getSkipEmbed(guild, event.getMember())).queue();
            replyType = ReplyType.SEPARATED;
        }
        else {
            replyType = ReplyType.REPLY;
        }

        QueueHandler.sendEmbed(event, replyType);
    }
}
