package com.safjnest.Commands.ManageMembers;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.Utilities.JSONReader;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.requests.ErrorResponse;

public class ModifyNickname extends Command {
    /**
     * Default constructor for the class.
     */
    public ModifyNickname() {
        this.name = this.getClass().getSimpleName();
        this.aliases = new JSONReader().getArray(this.name, "alias");
        this.help = new JSONReader().getString(this.name, "help");
        this.cooldown = new JSONReader().getCooldown(this.name);
        this.category = new Category(new JSONReader().getString(this.name, "category"));
        this.arguments = new JSONReader().getString(this.name, "arguments");
    }

    @Override
    protected void execute(CommandEvent event) {
        Member theGuy;
        String[] args = event.getArgs().split(" ", 3);
        
        if(args.length < 2)
            return;
        else if(event.getMessage().getMentionedMembers().size() > 0)
            theGuy = event.getMessage().getMentionedMembers().get(0);
        else
            theGuy = event.getGuild().getMemberById(args[0]);
        
        try {
            theGuy.modifyNickname(args[1]).queue(
            (e) -> event.reply("Cambio di nick di  " + theGuy.getAsMention() + " effettuato"), 
            new ErrorHandler().handle(
                ErrorResponse.MISSING_PERMISSIONS,
                    (e) -> event.replyError("Sorry, " + e.getMessage()))
            );
        } catch (Exception e) {
            event.replyError("Sorry, " + e.getMessage());
        }
    }
}