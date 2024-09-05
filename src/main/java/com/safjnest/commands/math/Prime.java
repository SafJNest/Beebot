package com.safjnest.commands.math;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;
import com.safjnest.util.SafJNest;

import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.FileUpload;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * 
 * @since 1.1
 */
public class Prime extends SlashCommand {

    public Prime(int maxPrime){
        this.name = this.getClass().getSimpleName().replace("Slash", "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();

        this.options = Arrays.asList(
            new OptionData(OptionType.INTEGER, "value", "Number of bits of the prime to generate", true)
                .setMinValue(2)
                .setMaxValue(maxPrime)
        );

        commandData.setThings(this);
    }

	@Override
	protected void execute(SlashCommandEvent event) {
        event.deferReply(false).queue();
        String primi = SafJNest.getRandomPrime(event.getOption("value").getAsInt()).toString();
        if (primi.length() > 2000) {
            event.getHook().editOriginal("The prime number is too big for discord, so here's a file:")
                .setFiles(FileUpload.fromData(
                    primi.getBytes(StandardCharsets.UTF_8),
                    "prime.txt"
                )
            ).queue();
        } 
        else {
            event.getHook().editOriginal(primi).queue();
        }    
	}
}