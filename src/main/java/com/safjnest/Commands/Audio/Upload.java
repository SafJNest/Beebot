package com.safjnest.Commands.Audio;

import java.io.File;

import com.amazonaws.AmazonClientException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.Utilities.JSONReader;

import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 1.2.5
 */
public class Upload extends Command{
    private AmazonS3 s3Client;
    private String fileName;
    
    public Upload(AmazonS3 s3Client){
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
        if((fileName = event.getArgs()) == ""){
            event.reply("manca il nome");
            return;
        }

        event.reply("operativo e pronto a listenare");
        FileListener fileListener = new FileListener(event, fileName, event.getChannel(), s3Client);
        event.getJDA().addEventListener(fileListener);
	}
}

class FileListener extends ListenerAdapter {
    private String name;
    private AmazonS3 s3Client;
    private CommandEvent event;
    private MessageChannel channel;

    public FileListener(CommandEvent event, String name, MessageChannel channel, AmazonS3 s3Client ){
        this.name = name;
        this.s3Client = s3Client;
        this.event = event;
        this. channel = channel;
    }
    
    @Override
    public void onMessageReceived(MessageReceivedEvent e){
        
        if(e.getChannel().equals(channel)){ 
            if(e.getAuthor().isBot())
                return;
            if(e.getMessage().getAttachments().size() <= 0){
                event.reply("manca il file");
                e.getJDA().removeEventListener(this);
                return;
            }
            if(e.getMessage().getAttachments().get(0).getSize() > 1048576){
                event.reply("il file è troppo grosso (1mb max)");
                e.getJDA().removeEventListener(this);
                return;
            }
            /*
            if(.get(0).getKey().equals(name)){1u 
                event.reply("esiste gia");
                e.getJDA().removeEventListener(this);
                return;
            }*/ //TODO non funziona niente
            
            File saveFile = new File("rsc" + File.separator + "Upload" + File.separator + (name +"."+ e.getMessage().getAttachments().get(0).getFileExtension()));
            e.getMessage().getAttachments().get(0).downloadToFile(saveFile)
                .thenAccept(file -> {
                    System.out.println("Upload del file su aws s3 " + file.getName());
                    try {
                        PutObjectRequest request = new PutObjectRequest("thebeebox", name, file);
                        ObjectMetadata metadata = new ObjectMetadata();
                        metadata.setContentType("audio/mpeg");
                        metadata.addUserMetadata("name", name);
                        metadata.addUserMetadata("category", "we");
                        request.setMetadata(metadata);
                        s3Client.putObject(request);
                    }catch(AmazonClientException ace){
                        ace.printStackTrace();
                    }
                    file.delete();
                })
                .exceptionally(t -> { // handle failure
                    event.reply("errore nel caricamento del file");
                    t.printStackTrace();
                    e.getJDA().removeEventListener(this);
                    return null;
                });
            event.reply("file caricato con successo");
            e.getJDA().removeEventListener(this);
        }
    }
}