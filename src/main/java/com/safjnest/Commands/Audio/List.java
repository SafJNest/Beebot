package com.safjnest.Commands.Audio;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.Utilities.AwsS3;
import com.safjnest.Utilities.JSONReader;

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
        boolean isListMe = args[0].equals("me");
        String soundNames = "";
        int cont = 0;
        if(isListMe){
            alpha = s3Client.listObjectsByServer(event.getAuthor().getId(), event);
            Map<String, ArrayList<String>> sortedMap = new TreeMap<>(alpha);
            sortedMap.putAll(alpha);
            for(String serverName : sortedMap.keySet()) {
                soundNames += "**"+ serverName +"**" + ":\n";
                for(String soundName : sortedMap.get(serverName)){
                    soundNames += soundName + " - ";
                    cont++;
                }
                soundNames = soundNames.substring(0, soundNames.length()-3) + "\n";
            }
        }else{
            alpha = s3Client.listObjects(event.getGuild().getId());
            Map<String, ArrayList<String>> sortedMap = new TreeMap<>(alpha);
            sortedMap.putAll(alpha);
            soundNames = "**"+ event.getGuild().getName() +"**:\n";
            for(String k : sortedMap.keySet()) {
                //TODO TOGTLIERE IL BUZL ALFABRTICO
                for(String s : sortedMap.get(k)){
                    soundNames+= s + " - ";
                    cont++;
                }
            }
            soundNames = soundNames.substring(0, soundNames.length()-3);
        } 
        soundNames += "\nSuono totali: " + cont;
        event.reply(soundNames);
    }
}
