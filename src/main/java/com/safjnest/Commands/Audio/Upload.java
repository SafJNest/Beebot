package com.safjnest.Commands.Audio;

import java.io.File;

import com.amazonaws.AmazonClientException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.Utilities.AwsS3;
import com.safjnest.Utilities.JSONReader;
import com.safjnest.Utilities.PermissionHandler;
import com.safjnest.Utilities.PostgreSQL;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 1.2.5
 */
public class Upload extends Command{
    private AwsS3 s3Client;
    private String fileName;
    private PostgreSQL sql;
    
    public Upload(AwsS3 s3Client, PostgreSQL sql){
        this.name = this.getClass().getSimpleName();;
        this.aliases = new JSONReader().getArray(this.name, "alias");
        this.help = new JSONReader().getString(this.name, "help");
        this.cooldown = new JSONReader().getCooldown(this.name);
        this.category = new Category(new JSONReader().getString(this.name, "category"));
        this.arguments = new JSONReader().getString(this.name, "arguments");
        this.s3Client = s3Client;
        this.sql = sql;
    }
    
	@Override
	protected void execute(CommandEvent event) {
        if((fileName = event.getArgs()) == ""){
            event.reply("You have to write the name of the sound");
            return;
        }

        event.reply("Ok, now upload the sound here in mp3 or **opus** format");
        FileListener fileListener = new FileListener(event, fileName, event.getChannel(), s3Client.getS3Client(), sql);
        event.getJDA().addEventListener(fileListener);
	}
}

class FileListener extends ListenerAdapter {
    private String name;
    private AmazonS3 s3Client;
    private CommandEvent event;
    private MessageChannel channel;
    private float maxFileSize = 1049000; //in bytes
    private PostgreSQL sql;

    public FileListener(CommandEvent event, String name, MessageChannel channel, AmazonS3 s3Client, PostgreSQL sql){
        this.name = name;
        this.s3Client = s3Client;
        this.event = event;
        this. channel = channel;
        this.sql = sql;
    }
    
    @Override
    public void onMessageReceived(MessageReceivedEvent e){
        
        if(e.getChannel().equals(channel)){ 
            if(e.getAuthor().isBot())
                return;
            if(e.getMessage().getAttachments().size() <= 0){
                event.reply("You have to upload the sound, you can try again by reusing the command");
                e.getJDA().removeEventListener(this);
                return;
            }   
            if(e.getMessage().getAttachments().get(0).getSize() > maxFileSize && !PermissionHandler.isUntouchable(event.getAuthor().getId())){
                event.reply("The file is too big (" + maxFileSize/1048576 + "mb max)");
                e.getJDA().removeEventListener(this);
                return;
            }
            /*
            if(.get(0).getKey().equals(name)){1u 
                event.reply("esiste gia");
                e.getJDA().removeEventListener(this);
                return;
            }*/ //TODO non funziona niente
            File uploadFolder = new File("rsc" + File.separator + "Upload");
            if(!uploadFolder.exists())
                uploadFolder.mkdir();
            File saveFile = new File("rsc" + File.separator + "Upload" + File.separator + (name +"."+ e.getMessage().getAttachments().get(0).getFileExtension()));
            e.getMessage().getAttachments().get(0).downloadToFile(saveFile)
                .thenAccept(file -> {
                    System.out.println("Upload del file su aws s3 " + file.getName());
                    try {
                        name = e.getGuild().getId() + "/" + e.getAuthor().getId() +"/"+ name;
                        PutObjectRequest request = new PutObjectRequest("thebeebox", name, file);
                        ObjectMetadata metadata = new ObjectMetadata();
                        metadata.setContentType("audio/mpeg");
                        metadata.addUserMetadata("name", name);
                        metadata.addUserMetadata("guild", e.getGuild().getId());
                        metadata.addUserMetadata("author", e.getAuthor().getId());
                        metadata.addUserMetadata("format", e.getMessage().getAttachments().get(0).getFileExtension());
                        request.setMetadata(metadata);
                        s3Client.putObject(request);
                    }catch(AmazonClientException ace){
                        ace.printStackTrace();
                    }
                    file.delete();
                })
                .exceptionally(t -> { // handle failure
                    event.reply("An error occured in the upload of the file");
                    t.printStackTrace();
                    e.getJDA().removeEventListener(this);
                    return null;
                });
            event.reply("File uploaded succesfully");
            String query = "INSERT INTO sound_id(name_sound, discord_id)"
                    + "VALUES('"+name+"','"+event.getGuild().getId()+"');";
            sql.runQuery(query);
            e.getJDA().removeEventListener(this);
        }
    }

}