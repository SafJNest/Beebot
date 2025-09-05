package com.safjnest.commands.queue;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.core.audio.PlayerManager;
import com.safjnest.core.audio.QueueHandler;
import com.safjnest.core.audio.TrackScheduler;
import com.safjnest.core.audio.types.ReplyType;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.entities.Guild;

public class Skip extends SlashCommand {
    
    public Skip() {
        this.name = this.getClass().getSimpleName().toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(this.name);
        
        this.aliases = commandData.getAliases();
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        this.arguments = commandData.getArguments();

        commandData.setThings(this);
    }

    @Override
    protected void execute(CommandEvent event) {
        Guild guild = event.getGuild();
        TrackScheduler ts = PlayerManager.get().getGuildMusicManager(guild).getTrackScheduler();
        AudioTrack nextTrack = ts.moveCursor(1);
        
        if(nextTrack == null) {
            event.reply("This is the end of the queue");
            return;
        }

        ts.play(nextTrack, true);

        event.reply(QueueHandler.getSkipEmbed(guild, event.getMember()));
        
        QueueHandler.sendEmbed(event);
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
