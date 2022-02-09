package com.safjnest.Commands.Math;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.Utilities.SafJNest;

public class FastestRoot extends Command{
    public FastestRoot(){
        this.name = "fastinversesquareroot";
        this.aliases = new String[]{"radiceinversa", "invroot", "fisqrt"};
        this.help = "il bot ti outplaya dicendoti la raadice quadrata inversa molto velocemente.";
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