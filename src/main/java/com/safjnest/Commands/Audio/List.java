package com.safjnest.Commands.Audio;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import com.google.api.services.youtube.model.Member;
import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.Utilities.AwsS3;
import com.safjnest.Utilities.JSONReader;
import com.safjnest.Utilities.PostgreSQL;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * 
 * @since 1.1
 */
public class List extends Command {
    private AwsS3 s3Client;
    private PostgreSQL sql;

    public List(AwsS3 s3Client, PostgreSQL sql){
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
        String query = "";
        String soundNames = "";
        int cont = 0;
        if(event.getArgs().equals("server"))
            query = "SELECT id, name, guild_id, user_id, extension FROM sound WHERE guild_id = '"+event.getGuild().getId()+"';";
        else if(event.getArgs().equals("me"))
            query = "SELECT id, name, guild_id, user_id, extension FROM sound WHERE user_id = '"+event.getAuthor().getId()+"';";
        else if(event.getArgs().equals("global"))
            query = "SELECT id, name, guild_id, user_id, extension FROM sound;";
        else if(event.getArgs().split(" ")[0].equals("user")){
            User theGuy = null;
            if(event.getMessage().getMentions().getUsers().size() > 0){
                theGuy = event.getMessage().getMentions().getUsers().get(0);
            }else{
                try {
                    theGuy = event.getJDA().getUserById(event.getArgs().split(" ")[1]);
                } catch (Exception e) {
                    event.reply("ID not found");
                }
            }
            //TODO: qualcosina ogni tanto da errore ora devo andare non faccio in tempo HEHE SAFj
            query = "SELECT id, name, guild_id, user_id, extension FROM sound WHERE user_id = '"+theGuy.getId()+"';";
        }
        ArrayList<ArrayList<String>> arr = sql.getTuple(query, 5);
        HashMap<String, ArrayList<String>> alpha = new HashMap<>();
        for(int i = 0; i < arr.size(); i++){
            if(!alpha.containsKey(arr.get(i).get(2)))
                alpha.put(arr.get(i).get(2), new ArrayList<>());
            alpha.get(arr.get(i).get(2)).add(arr.get(i).get(1));
        }
        Map<String, ArrayList<String>> sortedMap = new TreeMap<>(alpha);
        sortedMap.putAll(alpha);
        for(String serverId : sortedMap.keySet()) {
            String serverName = (event.getJDA().getGuildById(serverId)!=null) ? event.getJDA().getGuildById(serverId).getName() : "Im not in the server"; 
            soundNames += "**"+ serverName +"**" + ":\n";
            for(String soundName : sortedMap.get(serverId)){
                soundNames += soundName + " - ";
                cont++;
            }
            soundNames = soundNames.substring(0, soundNames.length()-3) + "\n";
        }
        soundNames += "\nTotal sounds: " + cont;
        event.reply(soundNames);
    }
}
//TODO rifare la classe list e AWSS3 perche' sono merdose
//OIDOZIRF