package com.safjnest.SlashCommands.Queue;

import java.util.Arrays;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.Utilities.CommandsLoader;
import com.safjnest.Utilities.Audio.PlayerManager;
import com.safjnest.Utilities.Audio.QueueHandler;
import com.safjnest.Utilities.Audio.TrackScheduler;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 */
public class JumpToSlash extends SlashCommand {

    public JumpToSlash(){
        this.name = this.getClass().getSimpleName().replace("Slash", "").toLowerCase();
        this.aliases = new CommandsLoader().getArray(this.name, "alias");
        this.help = new CommandsLoader().getString(this.name, "help");
        this.cooldown = new CommandsLoader().getCooldown(this.name);
        this.category = new Category(new CommandsLoader().getString(this.name, "category"));
        this.arguments = new CommandsLoader().getString(this.name, "arguments");
        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "position", "Select a song from the queue", true)
                .setAutoComplete(true)
        );
    }

	@Override
	protected void execute(SlashCommandEvent event) {
        Guild guild = event.getGuild();

        int position = Integer.parseInt(event.getOption("position").getAsString());
        TrackScheduler ts = PlayerManager.get().getGuildMusicManager(guild).getTrackScheduler();

        position--;
        ts.getPlayer().stopTrack();
        ts.play(ts.moveCursor(position - ts.getIndex()));
        
        QueueHandler.sendEmbed(event, false);
	}
}