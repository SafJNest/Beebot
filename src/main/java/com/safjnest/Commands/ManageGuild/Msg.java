package com.safjnest.Commands.ManageGuild;

import java.awt.Color;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;

public class Msg extends Command {

    public Msg(){
        this.name = "msg";
        this.aliases = new String[]{"messaggio", "message"};
        this.help = "il bot manda un messaggio ad uno user [/msg user messaggio]";
    }

	@Override
	protected void execute(CommandEvent event) {
        User theGuy = null;
        String[] command = event.getArgs().split(" ", 2);
        if(event.getMessage().getMentionedMembers().size() > 0)
                theGuy = event.getMessage().getMentionedMembers().get(0).getUser();
            else
                theGuy = event.getJDA().retrieveUserById(command[0]).complete();
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("NEW MESSAGE FROM " + event.getAuthor().getAsTag());
        eb.setThumbnail(event.getAuthor().getAvatarUrl());
        eb.setDescription(command[1]);
        eb.setColor(new Color(3, 252, 169));
        theGuy.openPrivateChannel().queue((privateChannel) -> privateChannel.sendMessageEmbeds(eb.build()).queue());
	}
}