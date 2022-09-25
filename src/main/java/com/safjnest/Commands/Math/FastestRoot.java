package com.safjnest.Commands.Math;

import com.safjnest.Utilities.JSONReader;
import com.safjnest.Utilities.SafJNest;
import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 1.1
 */
public class FastestRoot extends Command{
    public FastestRoot(){
        this.name = this.getClass().getSimpleName();
        this.aliases = new JSONReader().getArray(this.name, "alias");
        this.help = new JSONReader().getString(this.name, "help");
        this.cooldown = new JSONReader().getCooldown(this.name);
        this.category = new Category(new JSONReader().getString(this.name, "category"));
        this.arguments = new JSONReader().getString(this.name, "arguments");
    }

    @Override
    protected void execute(CommandEvent event) {
        try {
            event.reply(String.valueOf(SafJNest.fastInvSquareRoot(Float.parseFloat(event.getArgs()))));
        } catch (NumberFormatException e) {
            event.replyError("Only insert a positive number and not longer than 10 digits");
        } catch (Exception e) {
            event.replyError("sorry, " + e.getMessage());
        }
    }
}