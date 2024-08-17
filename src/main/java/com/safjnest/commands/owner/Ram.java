package com.safjnest.commands.owner;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;

/**
 * The commands sends the information about the ram usage of the bot.
 * <ul>
 * <li>{@code Total} - The total amount of ram that the bot can uses</li>
 * <li>{@code Used} - The amount of ram used by the bot</li>
 * <li>{@code Free} - Total-used</li>
 * <li>{@code Max} - The max amount of ram that can be used by java</li>
 * 
 * </ul>
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * 
 * @since 1.1.02
 */
public class Ram extends Command{
    /**
     * Default constructor for the class.
     */
    public Ram(){
        this.name = this.getClass().getSimpleName().toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(this.name);
        
        this.aliases = commandData.getAliases();
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        this.arguments = commandData.getArguments();
        this.ownerCommand = true;
        this.hidden = true;
        commandData.setThings(this);
    }
    /**
     * This method is called every time a member executes the command.
    */
     @Override
    protected void execute(CommandEvent e) {
        e.reply("Total: " + String.valueOf((Runtime.getRuntime().totalMemory())/1048576) + "mb\n"
            + "Usage: " + String.valueOf((Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory())/1048576) + "mb\n"
            + "Free: " + String.valueOf((Runtime.getRuntime().freeMemory())/1048576) + "mb\n"
            + "Max: " + String.valueOf(Runtime.getRuntime().maxMemory()/1048576) + "mb");
    }
}