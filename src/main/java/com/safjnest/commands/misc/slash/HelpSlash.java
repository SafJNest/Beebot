package com.safjnest.commands.misc.slash;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.commands.misc.Help;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.LayoutComponent;

/**
 * This commands once is called sends a message with a full list of all
 * commands, grouped by category.
 * <p>
 * The user can then use the command to get more information about a specific
 * command.
 * </p>
 * 
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * 
 * @since 1.1.01
 */
public class HelpSlash extends SlashCommand {

    public HelpSlash() {
        this.name = this.getClass().getSimpleName().replace("Slash", "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();

        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "command", "Name of the command you want information on",
                false)
                .setAutoComplete(true)
        );

        commandData.setThings(this);
        
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        String inputCommand = (event.getOption("command") == null) ? "" : event.getOption("command").getAsString();

        EmbedBuilder eb = new EmbedBuilder();
        List<LayoutComponent> rows = null;

        if(inputCommand.equals("")) 
            eb = Help.getGenericHelp(event.getGuild().getId(), event.getMember().getUser().getId());  
        else {      
            HashMap<String, BotCommand> commands = CommandsLoader.getCommandsData(event.getMember().getId());
            BotCommand commandToPrint = Help.searchCommand(inputCommand, commands);

            eb = Help.getCommandHelp(commandToPrint);
            rows = Help.getCommandButton(commandToPrint);
        }
        
        if (rows != null) event.deferReply().addEmbeds(eb.build()).setComponents(rows).queue();
        else event.deferReply().addEmbeds(eb.build()).queue();
    }

}
