package com.safjnest.Commands;

import java.awt.Color;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.Utilities.PermissionHandler;

import net.dv8tion.jda.api.EmbedBuilder;

public class BugsNotifier extends Command {

    public BugsNotifier(){
        this.name = "bugs";
        this.aliases = new String[]{"baco", "bughi", "report", "rep"};
        this.help = "il bot inviera' un messaggio ai due dei creatori contenente il bug";
    }

	@Override
	protected void execute(CommandEvent event) {
        String[] commandArray = event.getArgs().split(" ", 2);
        if(commandArray.length < 2) {
            event.reply("Descrivi il bug\nUsare il seguente formato [bugs] [nome comando] [descrizione]");
            return;
        }
        
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("BUGS ALERT "+commandArray[0]);
        eb.setAuthor(event.getAuthor().getName() + " from " + event.getGuild().getName());
        eb.setThumbnail(event.getAuthor().getAvatarUrl());
        eb.setDescription(commandArray[1]);
        eb.setColor(new Color(255, 0, 0));

        PermissionHandler.getUntouchables().forEach((id) -> event.getJDA().retrieveUserById(id).complete().openPrivateChannel().queue((privateChannel) -> privateChannel.sendMessageEmbeds(eb.build()).queue()));
	}
}