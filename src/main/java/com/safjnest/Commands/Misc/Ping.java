package com.safjnest.Commands.Misc;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;

public class Ping extends Command{

    public Ping(){
        this.name = "ping";
        this.aliases = new String[]{"pong", "pingpong"};
        this.help = "Restituisce il ping del bot.";
    }

    @Override
    protected void execute(CommandEvent e) {
        long time = System.currentTimeMillis();
        e.reply("Pong!", response -> {
            response.editMessageFormat("Pong: %d ms", System.currentTimeMillis() - time).queue();
        });
    }
}