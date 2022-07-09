package com.safjnest.Commands.Audio;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.Utilities.AwsS3;
import com.safjnest.Utilities.JSONReader;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

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
        }else if(args[0].equalsIgnoreCase("server") || args[0].equalsIgnoreCase("")){
            Guild guild = null;
            if(args.length > 0){
                try {
                    guild = event.getJDA().getGuildById(args[1]);
                } catch (Exception e) {
                    guild = event.getGuild();
                }
                if(guild == null)
                    guild = event.getGuild();
            }
            else
                guild = event.getGuild();

            ArrayList<String> sounds = s3Client.listObjects(guild.getId());
            soundNames = "**" + guild.getName() + "**:\n";
            for(String s : sounds){
                soundNames+= s + " - ";
                cont++;
            }
            if(sounds.size() > 0)
                soundNames = soundNames.substring(0, soundNames.length() - 3);
        }else if(args[0].equalsIgnoreCase("user")){
            User theGuy = null;
            if(event.getMessage().getMentionedMembers().size() > 0){
                theGuy = event.getJDA().getUserById(event.getMessage().getMentionedMembers().get(0).getId());
                if(theGuy == null)
                    theGuy = event.getAuthor();
            }else if(args.length > 0){
                try {
                    theGuy = event.getJDA().getUserById(args[1]);
                } catch (Exception e) {
                    theGuy = event.getAuthor();
                }
                if(theGuy == null)
                    theGuy = event.getAuthor();
            }
            else
                theGuy = event.getAuthor();
            alpha = s3Client.listObjectsByServer(theGuy.getId(), event);
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
        }else if(args[0].equalsIgnoreCase("global")){
            alpha = s3Client.listObjectsByServer();
            Map<String, ArrayList<String>> sortedMap = new TreeMap<>(alpha);
            sortedMap.putAll(alpha);
            for(String serverName : sortedMap.keySet()) {
                soundNames += "**"+ 
                ((event.getJDA().getGuildById(serverName) == null)
                    ? "Nome non disponibile"
                    : event.getJDA().getGuildById(serverName).getName()) +"**" + ":\n";
                for(String soundName : sortedMap.get(serverName)){
                    soundNames += soundName.split("/")[2] + " - ";
                    cont++;
                }
                soundNames = soundNames.substring(0, soundNames.length()-3) + "\n";
            }
        }
        soundNames += "\nSuono totali: " + cont;
        event.reply(soundNames);
    }
}
//TODO rifare la classe list e AWSS3 perche' sono merdose
//OIDOZIRF