package com.safjnest.commands.audio.greet;

import java.util.Arrays;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.core.cache.managers.UserdataCache;
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
public class GreetUnset extends SlashCommand{

    public GreetUnset(String father){
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(father).getChild(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();

        this.options = Arrays.asList(
            new OptionData(OptionType.BOOLEAN, "global", "true for global, false for guild only, false by default", false)
        );

        commandData.setThings(this);
    }

	@Override
	public void execute(SlashCommandEvent event) {
        String guildId = (event.getOption("global") != null && event.getOption("global").getAsBoolean()) ? "0" : event.getGuild().getId();

        if (!UserdataCache.getUser(event.getUser().getId()).unsetGreet(guildId)) {
            event.deferReply(false).addContent("An error occurred while unsetting the greet").queue();
            return;
        }

        event.deferReply(false).addContent("Greet has been unset").queue();
    }
}