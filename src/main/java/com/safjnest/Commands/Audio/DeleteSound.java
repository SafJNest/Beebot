package com.safjnest.Commands.Audio;


import com.amazonaws.services.s3.AmazonS3;
import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
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
    private AmazonS3 s3Client;
    
    public DeleteSound(AmazonS3 s3Client){
        this.name = this.getClass().getSimpleName();;
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
            event.reply("Non sei autorizzato a deletare i suoni testa di merda");
            return;
        }
        if((fileName = event.getArgs()) == ""){
            event.reply("manca il nome");
            return;
        }
        
        s3Client.deleteObject("thebeebox", event.getGuild().getId() + "/" + event.getAuthor().getId() + "/" + fileName );
        event.reply(fileName + " sgozzato con successo");

	}
}
