package com.safjnest.Commands.Math;

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
        this.name = "fastinversesquareroot";
        this.aliases = new String[]{"radiceinversa", "invroot", "fisqrt"};
        this.help = "Consente di stampare il risultato della radice quadrata inversa veloce.";
        this.category = new Category("math");
        this.arguments = "[fastinversesquareroot] [numero]";
    }

    @Override
    protected void execute(CommandEvent event) {
        try {
            event.reply(String.valueOf(SafJNest.fastInvSquareRoot(Float.parseFloat(event.getArgs()))));
        } catch (NumberFormatException e) {
            event.replyError("Metti un numero al massimo a 10 cifre e non negativo. (Usa il punto non la virgola)");
        } catch (Exception e) {
            event.replyError("sorry, " + e.getMessage());
        }
    }
}