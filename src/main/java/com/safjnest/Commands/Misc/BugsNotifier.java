package com.safjnest.Commands.Misc;

import java.awt.Color;

import com.safjnest.Utilities.PermissionHandler;
import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;

import net.dv8tion.jda.api.EmbedBuilder;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * 
 * @since 1.1
 */
public class BugsNotifier extends Command {

    public BugsNotifier(){
        this.name = "bugs";
        this.aliases = new String[]{"baco", "bughi", "report", "rep"};
        this.help = "Consente di inviare un messaggio ai due developer del bot, con una accurata descrizione, le problematiche avute riguardante un comando.\n"
        + "Per evitare possibili spam ha un cooldown di 100s.";
        this.cooldown = 100;
        this.category = new Category("Misc");
        this.arguments = "[bugs] [nome comando] [descrizione]";
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