package com.safjnest.commands.audio.slash.greet;

import java.util.Arrays;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.core.Bot;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;

import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 1.1
 */
public class GreetSetSlash extends SlashCommand {

    public GreetSetSlash(String father) {
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(father).getChild(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();

        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "sound", "Sound to use", true)
                .setAutoComplete(true),
            new OptionData(OptionType.BOOLEAN, "global", "true for global, false for guild only, false by default", false)
        );

        commandData.setThings(this);
    }

	@Override
	public void execute(SlashCommandEvent event) {
        String sound = event.getOption("sound").getAsString();

        String guildId = (event.getOption("global") != null && event.getOption("global").getAsBoolean()) ? "0" : event.getGuild().getId();

        if (!Bot.getUserData(event.getUser().getId()).setGreet(guildId, sound)) {
            event.deferReply(false).addContent("An error occurred while setting the greet").queue();
            return;
        }

        event.deferReply(false).addContent("Greet has been set").queue();
    }
}