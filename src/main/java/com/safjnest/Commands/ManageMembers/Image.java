package com.safjnest.Commands.ManageMembers;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;

import net.dv8tion.jda.api.entities.User;

public class Image extends Command{
    public Image(){
        this.name = "image";
        this.aliases = new String[]{"img"};
        this.help = "il bot ti outplaya veramente forte e finisci a strisciare fuori dal server (senza la possibilità di rientrare)";
    }

    @Override
    protected void execute(CommandEvent event) {
        User theGuy = null;
        try {
            if(event.getMessage().getMentionedMembers().size() > 0)
                theGuy = event.getMessage().getMentionedMembers().get(0).getUser();
            else
                theGuy = event.getJDA().retrieveUserById(event.getArgs()).complete();
            event.reply(theGuy.getAvatarUrl());
        } catch (Exception e) {
            event.reply("sorry, " + e.getMessage());
        }
    }
}