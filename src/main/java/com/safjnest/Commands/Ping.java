package com.safjnest.Commands;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;

public class Ping extends Command{

    public Ping(){
        this.name = "ping";
        this.aliases = new String[]{"pong", "pingpong"};
        this.help = "il ping pong bro";
    }

    @Override
    protected void execute(CommandEvent e) {
        long time = System.currentTimeMillis();
        e.reply("Pong!", response -> {
            response.editMessageFormat("Pong: %d ms", System.currentTimeMillis() - time).queue();
        });
    }
}