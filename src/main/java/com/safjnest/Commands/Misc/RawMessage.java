package com.safjnest.Commands.Misc;

import java.util.List;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.Utilities.JSONReader;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageHistory;

/**
 * This commands gets the raw textual content of the last message sent a user.
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * @since 1.2
 */
public class RawMessage extends Command{
    /**
     * Default constructor for the class.
     */
    public RawMessage(){
        this.name = this.getClass().getSimpleName();
        this.aliases = new JSONReader().getArray(this.name, "alias");
        this.help = new JSONReader().getString(this.name, "help");
        this.cooldown = new JSONReader().getCooldown(this.name);
        this.category = new Category(new JSONReader().getString(this.name, "category"));
        this.arguments = new JSONReader().getString(this.name, "arguments");
    }

    /**
     * This method is called every time a member executes the command.
     */
    @Override
    protected void execute(CommandEvent event) {
        MessageHistory history = new MessageHistory(event.getChannel());
        List<Message> msgs = history.retrievePast(2).complete();
        event.reply(msgs.get(1).getContentRaw());
    }
}
