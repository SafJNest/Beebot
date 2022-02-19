package com.safjnest.Commands.Math;

import com.safjnest.Utilities.SafJNest;
import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * 
 * @since 1.0
 */
public class DAC extends Command{
    public DAC(){
        this.name = "dac";
        this.aliases = new String[]{"divideandconquer"};
        this.help = "Consente di stampare il numero di cifre di un numero intero(int) fino ad un max di 2^32.";
        this.category = new Category("Matematica");
        this.arguments = "[dac] [numero]";
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