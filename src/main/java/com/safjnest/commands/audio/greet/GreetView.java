package com.safjnest.commands.audio.greet;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.core.audio.SoundHandler;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 1.1
 */
public class GreetView extends SlashCommand {

    public GreetView(String father) {
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(father).getChild(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();

        commandData.setThings(this);
    }

	@Override
	public void execute(SlashCommandEvent event) {
        event.deferReply(false).addEmbeds(SoundHandler.getGreetViewEmbed(event.getUser().getId(), event.getGuild().getId()).build())
                .addComponents(SoundHandler.getGreetButton(event.getUser().getId(), event.getGuild().getId())).queue();
    }
}