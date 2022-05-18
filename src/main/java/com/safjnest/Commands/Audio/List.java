package com.safjnest.Commands.Audio;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.awt.Color;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.Utilities.AwsS3;
import com.safjnest.Utilities.JSONReader;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageChannel;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * 
 * @since 1.1
 */
public class List extends Command {
    private AwsS3 s3Client;

    public List(AwsS3 s3Client){
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
        HashMap<String, ArrayList<String>> alpha = null;
        String[] args = event.getArgs().split(" ");
        if(args[0].equals("me"))
            alpha = s3Client.listObjectsByServer(event.getAuthor().getId(), event);
        else
            alpha = s3Client.listObjects(event.getGuild().getId());
        
        Map<String, ArrayList<String>> sortedMap = new TreeMap<>(alpha);
        sortedMap.putAll(alpha);
        MessageChannel channel = event.getChannel();
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("SoundBoard");
        String soundNames = "";
            for(String k : sortedMap.keySet()) {
                soundNames += "**"+ k +"**" + ":\n";
                for(String s : sortedMap.get(k)){
                    soundNames+= s + " - ";
                }
                soundNames+="\n";
                //eb.addField(k, soundNames, true);
            }
            System.out.println(soundNames);
            event.reply(soundNames);
        /*eb.setDescription("Lista con tutti i suoni del tier 1 bot");
        eb.setColor(new Color(0, 128, 128));
        eb.setAuthor(event.getSelfUser().getName(), "https://github.com/SafJNest",event.getSelfUser().getAvatarUrl());
        eb.setFooter("*Questo non e' soundfx, questa e' perfezione cit. steve jobs (probabilmente)", null);
        File file = new File("rsc" + File.separator + "img" + File.separator + "mp3.png");
        eb.setThumbnail("attachment://mp3.png");
        channel.sendMessageEmbeds(eb.build())
                    .addFile(file, "mp3.png")
                    .queue();
        */
	}
}
