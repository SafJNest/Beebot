package com.safjnest.Commands.Audio;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.Utilities.AwsS3;
import com.safjnest.Utilities.JSONReader;

import net.dv8tion.jda.api.Permission;

/**
 * The command lets you delete a sound from the server.
 * <p>You have to be a server admin to use the command.</p>
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * 
 * @since 1.3
 */
public class DeleteSound extends Command{
    private AwsS3 s3Client;
    
    public DeleteSound(AwsS3 s3Client){
        this.name = this.getClass().getSimpleName();
        this.aliases = new JSONReader().getArray(this.name, "alias");
        this.help = new JSONReader().getString(this.name, "help");
        this.cooldown = new JSONReader().getCooldown(this.name);
        this.category = new Category(new JSONReader().getString(this.name, "category"));
        this.arguments = new JSONReader().getString(this.name, "arguments");
        this.s3Client = s3Client;
    }
    
	@Override
	protected void execute(CommandEvent event) {
        String fileName = "";
        if(!event.getGuild().getMember(event.getJDA().getSelfUser()).hasPermission(Permission.MANAGE_SERVER)){
            event.reply("You don't have permission to manage the server");
            return;
        }
        if((fileName = event.getArgs()) == ""){
            event.reply("Missing sound's name.");
            return;
        }
        
        s3Client.getS3Client().deleteObject("thebeebox", event.getGuild().getId() + "/" + event.getAuthor().getId() + "/" + fileName );
        event.reply(fileName + " deleted.");

	}
}
