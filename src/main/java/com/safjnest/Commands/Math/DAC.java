package com.safjnest.Commands.Math;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.Utilities.SafJNest;

public class DAC extends Command{
    public DAC(){
        this.name = "dac";
        this.aliases = new String[]{"divideandconquer"};
        this.help = "il bot ti outplaya dicendoti le cifre di un numero intero fino ad un max di 10 cifre.";
    }

    @Override
    protected void execute(CommandEvent event) {
        try {
            event.reply(String.valueOf(SafJNest.divideandconquer(Integer.parseInt(event.getArgs()))));
        } catch (NumberFormatException e) {
            event.replyError("Metti un numero al massimo a 10 cifre");
        } catch (Exception e) {
            event.replyError("sorry, " + e.getMessage());
        }
    }
}