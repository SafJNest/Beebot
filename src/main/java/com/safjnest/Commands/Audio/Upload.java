package com.safjnest.Commands.Audio;

import java.io.File;

import com.amazonaws.AmazonClientException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;

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
        this.name = "upload";
        this.aliases = new String[]{"up", "add"};
        this.help = "Il comando consente di poter caricare facilmente dei suoni nel database del bot.\n"
        + "Se carichi dei file mp3 ci fai un piacere.\n"
        + "Se carichi dei .opus ti sgozzo.";
        this.category = new Category("Audio");
        this.arguments = "[upload] [nome del suono, senza specificare il formato]";
        this.s3Client = s3Client;
    }
    
	@Override
	protected void execute(CommandEvent event) {
        if((fileName = event.getArgs()) == ""){
            event.reply("il nome idiota");
            return;
        }

        event.reply("operativo e pronto a listenare");
        //TODO se ce n'è uno attivo fai esplodere cose

        FileListener fileListener = new FileListener(event, fileName, s3Client);
        event.getJDA().addEventListener(fileListener);
	}
}

class FileListener extends ListenerAdapter {
    private String name;
    private AmazonS3 s3Client;
    private CommandEvent event;

    public FileListener(CommandEvent event, String name, AmazonS3 s3Client){
        this.name = name;
        this.s3Client = s3Client;
        this.event = event;
    }
    
    @Override
    public void onMessageReceived(MessageReceivedEvent e){
        if(e.getAuthor().isBot()){
            return;
        }

        if(e.getMessage().getAttachments().size() <= 0){
            event.reply("il file idiota");
            e.getJDA().removeEventListener(this);
            return;
        }

        File saveFile = new File("upload" + File.separator + (name +"."+ e.getMessage().getAttachments().get(0).getFileExtension()));
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
                t.printStackTrace();
                e.getJDA().removeEventListener(this);
                return null;
            });
        event.reply("tutto apposto man");
        e.getJDA().removeEventListener(this);
    }
}