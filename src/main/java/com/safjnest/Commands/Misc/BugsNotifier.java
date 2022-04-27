package com.safjnest.Commands.Misc;

import java.awt.Color;

import com.safjnest.Utilities.JSONReader;
import com.safjnest.Utilities.PermissionHandler;
import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;

import net.dv8tion.jda.api.EmbedBuilder;

/**
 * This command let the user send a message to the {@link com.safjnest.Utilities.PermissionHandler#untouchables developers}
 * about a bug that occurs with a command.
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * 
 * @since 1.1
 */
public class BugsNotifier extends Command {
    /**
     * Default constructor for the class.
     */
    public BugsNotifier(){
        this.name = this.getClass().getSimpleName();
        this.aliases = new JSONReader().getArray(this.name, "alias");
        this.help = new JSONReader().getString(this.name, "help");
        this.cooldown = new JSONReader().getCooldown(this.name);
        this.category = new Category(new JSONReader().getString(this.name, "category"));
        this.arguments = new JSONReader().getString(this.name, "arguments");
    }
    /**
     * This method is called every time a member executes the command.
     */
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