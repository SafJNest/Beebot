package com.safjnest.Commands.Audio;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.awt.Color;

import com.safjnest.Utilities.SoundBoard;
import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;


import com.mpatric.mp3agic.Mp3File;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageChannel;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * 
 * @since 1.1
 */
public class List extends Command {

    public List(){
        this.name = "list";
        this.aliases = new String[]{"listoide", "listina","lista", "listona"};
        this.help = "Il bot invia la lista di tutti i suoni locali.";
        this.category = new Category("Audio");
        this.arguments = "[list] (album)";
    }

	@Override
	protected void execute(CommandEvent event) {
        MessageChannel channel = event.getChannel();
        HashMap<String, ArrayList<Mp3File>> tags = new HashMap<>();
        Mp3File[] files = SoundBoard.getMP3File();
        String[] args = event.getArgs().split(" ",2);
        //sorting by album
        if(args[0].equalsIgnoreCase("album")){
            for (Mp3File file : files){
                String tag = (file.getId3v2Tag().getAlbum() == null) ? "Misc" : file.getId3v2Tag().getAlbum();
                if(!tags.containsKey(tag))
                    tags.put(tag, new ArrayList<Mp3File>());
                tags.get(tag).add(file);
            }
        }else{
            //sorting by artist
            for (Mp3File file : files){
                String tag = (file.getId3v2Tag().getAlbumArtist() == null) ? "Misc" : file.getId3v2Tag().getAlbumArtist();
                if(!tags.containsKey(tag))
                    tags.put(tag, new ArrayList<Mp3File>());
                tags.get(tag).add(file);
            }
        }
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("SoundBoard");
    
        String soundNames = "```\n";
        int size = tags.size();
        if(!args[0].equalsIgnoreCase("album") && !args[0].equalsIgnoreCase("")){
            if(tags.containsKey(args[0])){
                eb.setDescription("Lista con tutti i suoni di " + args[0]);
                for(Mp3File f : tags.get(args[0]))
                    soundNames+= f.getId3v2Tag().getTitle() + "\n";
                soundNames = soundNames + "```\n";
                eb.addField(args[0], soundNames, true);
            }else{
                event.reply(args[0] + " NON ESISTE PEZZO DIEGTBUHYREW");
                return;    
            }
        }else if(args[0].equalsIgnoreCase("album") && !args[1].equalsIgnoreCase("")){
            if(tags.containsKey(args[1])){
                eb.setDescription("Lista con tutti i suoni dell'album " + args[0]);
                for(Mp3File f : tags.get(args[1]))
                    soundNames+= f.getId3v2Tag().getTitle() + "\n";
                soundNames = soundNames + "```\n";
                eb.addField(args[1], soundNames, true);
            }else{
                event.reply(args[1] + " NON ESISTE PEZZO DIEGTBUHYREW");
                return;    
            }
        
        }else{
            eb.setDescription("Lista con tutti i suoni del tier 1 bot");
            for(int i = 0; i < size; i++){
                String k = getMax(tags);
                for(Mp3File f : tags.get(k)){
                    soundNames+= f.getId3v2Tag().getTitle() + "\n";
                }
                soundNames+="```";
                eb.addField(k, soundNames, true);
                soundNames = "```\n";
                tags.remove(k);
            }
        }
        eb.setColor(new Color(0, 128, 128));
        eb.setAuthor(event.getSelfUser().getName(), "https://github.com/SafJNest",event.getSelfUser().getAvatarUrl());
        eb.setFooter("*Questo non e' soundfx, questa e' perfezione cit. steve jobs", null);
        File file = new File("img" + File.separator + "mp3.png");
        eb.setThumbnail("attachment://mp3.png");
        channel.sendMessageEmbeds(eb.build())
                    .addFile(file, "mp3.png")
                    .queue();
	}



    public static String getMax(HashMap<String, ArrayList<Mp3File>> tags){
        int max=0;
        String maxKey = "";

        for(String k : tags.keySet()){
            if(tags.get(k).size() > max){
                max = tags.get(k).size();
                maxKey = k;
            }
        }
        return maxKey;
    }
}
