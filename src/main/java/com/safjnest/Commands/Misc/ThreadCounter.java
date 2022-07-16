package com.safjnest.Commands.Misc;

import org.apache.commons.lang3.ThreadUtils;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.Utilities.JSONReader;
/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * 
 * @since 1.3
 */
public class ThreadCounter extends Command{
    /**
     * Default constructor for the class.
     */
    public ThreadCounter(){
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
    protected void execute(CommandEvent e) {
        double cont = 0.0;
        for (Thread t : ThreadUtils.getAllThreads()) {
            if(t.getName().startsWith("lava"))
                cont++;
        }
        e.reply("Thread lava player attivi: " + cont +"\nTotale pp eseguiti: " + cont/3);
    }
}