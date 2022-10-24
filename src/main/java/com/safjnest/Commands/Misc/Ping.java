package com.safjnest.Commands.Misc;



import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.Utilities.CommandsHandler;

/**
 * The commands shows the ping of the bot.
 * <p>The bot sends a message, once the message is received, the bot sends a message back, and the ping is calculated.</p>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 1.0
 */

public class Ping extends SlashCommand{
    /**
     * Default constructor for the class.
     */
    public Ping(){
        this.name = this.getClass().getSimpleName().toLowerCase();
        this.aliases = new CommandsHandler().getArray(this.name, "alias");
        this.help = new CommandsHandler().getString(this.name, "help");
        this.cooldown = new CommandsHandler().getCooldown(this.name);
        this.category = new Category(new CommandsHandler().getString(this.name, "category"));
        this.arguments = new CommandsHandler().getString(this.name, "arguments");
    }
    /**
     * This method is called every time a member executes the command.
     */
    @Override
    public void execute(SlashCommandEvent event) {
        
        long time = System.currentTimeMillis();
        event.deferReply(true).queue(
            hook -> hook.editOriginalFormat("Pong: %d ms ", System.currentTimeMillis() - time).queue()
        );
    }

   
    
}


