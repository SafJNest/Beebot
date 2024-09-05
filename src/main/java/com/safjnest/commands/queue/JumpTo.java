package com.safjnest.commands.queue;

import java.util.Arrays;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.core.audio.PlayerManager;
import com.safjnest.core.audio.QueueHandler;
import com.safjnest.core.audio.TrackScheduler;
import com.safjnest.core.audio.types.ReplyType;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;
import com.safjnest.util.SafJNest;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 */
public class JumpTo extends SlashCommand {

    public JumpTo() {
        this.name = this.getClass().getSimpleName().toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(this.name);
        
        this.aliases = commandData.getAliases();
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        this.arguments = commandData.getArguments();

        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "position", "Select a song from the queue", true)
                .setAutoComplete(true)
        );

        commandData.setThings(this);
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

    @Override
	protected void execute(SlashCommandEvent event) {
        Guild guild = event.getGuild();

        if(!SafJNest.intIsParsable(event.getOption("position").getAsString())) {
            event.deferReply().addContent("Write a number or use the autocomplete").queue();
            return;
        }

        int position = Integer.parseInt(event.getOption("position").getAsString());
        TrackScheduler ts = PlayerManager.get().getGuildMusicManager(guild).getTrackScheduler();

        position--;
        ts.getPlayer().stopTrack();
        ts.play(ts.moveCursor(position - ts.getIndex()));
        
        QueueHandler.sendEmbed(event, ReplyType.REPLY);
	}
}