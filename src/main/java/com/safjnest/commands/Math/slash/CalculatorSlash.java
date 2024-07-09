package com.safjnest.commands.Math.slash;

import java.util.Arrays;

import com.safjnest.commands.Math.Calculator;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;

import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;


/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * 
 * @since 1.1
 */
public class CalculatorSlash extends SlashCommand {

    public CalculatorSlash() {
        this.name = this.getClass().getSimpleName().replace("Slash", "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();

        this.options = Arrays.asList(
                new OptionData(OptionType.STRING, "n", "Number", true));

        commandData.setThings(this);
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        event.deferReply(false).addContent(String.valueOf(Calculator.evaluateExpression(event.getOption("n").getAsString()))).queue();
    }

    
}