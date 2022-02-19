package com.safjnest.Commands.ManageMembers;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;

import net.dv8tion.jda.api.entities.User;

/**
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 1.1
 */
public class Image extends Command{
    public Image(){
        this.name = "image";
        this.aliases = new String[]{"img"};
        this.help = "Il bot invia l'immagine profilo dello user.";
        this.category = new Category("Gestione Membri");
        this.arguments = "[image] [@user]";
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