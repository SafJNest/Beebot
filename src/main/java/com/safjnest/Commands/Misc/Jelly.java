package com.safjnest.Commands.Misc;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.Utilities.JSONReader;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.requests.ErrorResponse;
/**
 * 
 * @author <a href="https://github.com/Leon412">Leon412</a> 
 * @since 1.3
 */
public class Jelly extends Command {
    /**
     * Default constructor for the class.
     */
    public Jelly() {
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
        String[] args = event.getArgs().split(" ", 2);
        
        if(args[0].equals(""))
            theGuy = event.getMember();
        else if(event.getMessage().getMentionedMembers().size() > 0)
            theGuy = event.getMessage().getMentionedMembers().get(0);
        else
            theGuy = event.getGuild().getMemberById(args[0]);
        
        if(((theGuy.getNickname() == null) ? theGuy.getUser().getName() : theGuy.getNickname()).toLowerCase().endsWith("wx"))
            event.reply(theGuy.getAsMention() + " fa già parte del team di jelly");
        else{
            try {
                theGuy.modifyNickname(((theGuy.getNickname() == null) ? theGuy.getUser().getName() : theGuy.getNickname()) + "WX").queue(
                (e) -> event.reply("Jellificato  " + theGuy.getAsMention() + " blblblablvblbbla"), 
                new ErrorHandler().handle(
                    ErrorResponse.MISSING_PERMISSIONS,
                        (e) -> event.replyError("Sorry, " + e.getMessage()))
                );
            } catch (Exception e) {
                event.replyError("Sorry, " + e.getMessage());
            }
        }
    }
}