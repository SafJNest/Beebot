package com.safjnest.Commands.ManageGuild;

import java.awt.Color;
import java.io.File;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.Utilities.JSONReader;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;

/**
 * @author <a href="https://github.com/NeuntronSun">NeutronSun</a>
 * 
 * @since 1.3
 */
public class Anonym extends Command {

    public Anonym(){
        this.name = this.getClass().getSimpleName();
        this.aliases = new JSONReader().getArray(this.name, "alias");
        this.help = new JSONReader().getString(this.name, "help");
        this.cooldown = new JSONReader().getCooldown(this.name);
        this.category = new Category(new JSONReader().getString(this.name, "category"));
        this.arguments = new JSONReader().getString(this.name, "arguments");
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
        eb.setTitle("NEW ANONYMUS MESSAGE");
        String img = "punto.jpg";
        File file = new File("rsc" + File.separator + "img" + File.separator+ img);
        eb.setThumbnail("attachment://" + img);
        eb.setDescription(command[1]);
        eb.setColor(new Color(3, 252, 169));
        theGuy.openPrivateChannel().queue((privateChannel) -> privateChannel.sendMessageEmbeds(
            eb.build())
            .addFile(file,img)
            .queue());
	}
}