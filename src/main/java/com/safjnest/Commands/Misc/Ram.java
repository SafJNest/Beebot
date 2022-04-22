package com.safjnest.Commands.Misc;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.Utilities.JSONReader;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * 
 * @since 1.1.02
 */
public class Ram extends Command{

    public Ram(){
        this.name = this.getClass().getSimpleName();
        this.aliases = new JSONReader().getArray(this.name, "alias");
        this.help = new JSONReader().getString(this.name, "help");
        this.cooldown = new JSONReader().getCooldown(this.name);
        this.category = new Category(new JSONReader().getString(this.name, "category"));
        this.arguments = new JSONReader().getString(this.name, "arguments");
    }

    @Override
    protected void execute(CommandEvent e) {
        e.reply("Totale: " + String.valueOf((Runtime.getRuntime().totalMemory())/1048576) + "mb\n"
        + "In uso: " + String.valueOf((Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory())/1048576) + "mb\n"
        + "Libera: " + String.valueOf((Runtime.getRuntime().freeMemory())/1048576) + "mb\n"
        + "Massima: " + String.valueOf(Runtime.getRuntime().maxMemory()/1048576) + "mb");
    }
}