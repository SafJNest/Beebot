package com.safjnest.Commands.Misc;

<<<<<<< Updated upstream

import java.util.Map;

=======
>>>>>>> Stashed changes
import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.Utilities.CommandsHandler;

import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.commands.Command.Type;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.localization.LocalizationFunction;
import net.dv8tion.jda.api.interactions.commands.localization.LocalizationMap;
import net.dv8tion.jda.api.utils.data.DataObject;

/**
 * The commands shows the ping of the bot.
 * <p>The bot sends a message, once the message is received, the bot sends a message back, and the ping is calculated.</p>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 1.0
 */
<<<<<<< Updated upstream
public class Ping extends SlashCommand{
=======
public class Ping extends Command {
>>>>>>> Stashed changes

    /**
     * Default constructor for the class.
     */
    public Ping(){
        this.name = this.getClass().getSimpleName();
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
    protected void execute(SlashCommandEvent e) {
        long time = System.currentTimeMillis();
        /* 
        e.reply("Pong!", response -> {
            response.editMessageFormat("Pong: %d ms", System.currentTimeMillis() - time).queue();
        });
        */
        e.deferReply().addContent(String.valueOf(System.currentTimeMillis() - time)).queue();
    }
<<<<<<< Updated upstream
   
    
}
=======
}

>>>>>>> Stashed changes
