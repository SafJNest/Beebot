package com.safjnest.Commands.Misc;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;

public class Ram extends Command{

    public Ram(){
        this.name = "ram";
        this.aliases = new String[]{"usage"};
        this.help = "Restituisce le statistiche di sistema del bot, ram in uso, libera e totale (mb).";
    }

    @Override
    protected void execute(CommandEvent e) {
        e.reply("In uso: " + String.valueOf((Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory())/1048576) + "mb\n"
        + "Totale: " + String.valueOf((Runtime.getRuntime().totalMemory())/1048576) + "mb\n"
        + "Libera: " + String.valueOf((Runtime.getRuntime().freeMemory())/1048576) + "mb");
    }
}