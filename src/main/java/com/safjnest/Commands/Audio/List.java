package com.safjnest.Commands.Audio;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.awt.Color;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;



import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageChannel;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * 
 * @since 1.1
 */
public class List extends Command {
    private AmazonS3 s3Client;

    public List(AmazonS3 s3Client){
        this.name = "list";
        this.aliases = new String[]{"listoide", "listina", "lista", "listona"};
        this.help = "Il bot invia la lista di tutti i suoni locali.";
        this.category = new Category("Audio");
        this.arguments = "[list] (album)";
        this.s3Client = s3Client;
    }

	@Override
	protected void execute(CommandEvent event) {
        HashMap<String, ArrayList<String>> alpha = new HashMap<>();
        ListObjectsRequest listObjectsRequest = new ListObjectsRequest()
                .withBucketName("thebeebox");
            ObjectListing objectListing;
            do {
                objectListing = s3Client.listObjects(listObjectsRequest);
                for (S3ObjectSummary objectSummary : objectListing.getObjectSummaries()) {
                    if(!alpha.containsKey(String.valueOf(objectSummary.getKey().charAt(0)).toUpperCase()))
                        alpha.put(String.valueOf(objectSummary.getKey().charAt(0)).toUpperCase(), new ArrayList<String>());
                    alpha.get(String.valueOf(objectSummary.getKey().charAt(0)).toUpperCase()).add(objectSummary.getKey());
                }   
                listObjectsRequest.setMarker(objectListing.getNextMarker());
            } while (objectListing.isTruncated());
        Map<String, ArrayList<String>> sortedMap = new TreeMap<>(alpha);
        sortedMap.putAll(alpha);
        MessageChannel channel = event.getChannel();
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("SoundBoard");
        String soundNames = "```\n";
            for(String k : sortedMap.keySet()) {
                for(String s : sortedMap.get(k)){
                    soundNames+= s + "\n";
                }
                soundNames+="```";
                eb.addField(k, soundNames, true);
                soundNames = "```\n";
            }
        eb.setDescription("Lista con tutti i suoni del tier 1 bot");
        eb.setColor(new Color(0, 128, 128));
        eb.setAuthor(event.getSelfUser().getName(), "https://github.com/SafJNest",event.getSelfUser().getAvatarUrl());
        eb.setFooter("*Questo non e' soundfx, questa e' perfezione cit. steve jobs", null);
        File file = new File("img" + File.separator + "mp3.png");
        eb.setThumbnail("attachment://mp3.png");
        channel.sendMessageEmbeds(eb.build())
                    .addFile(file, "mp3.png")
                    .queue();
        
	}
}
