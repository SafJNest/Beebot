package com.safjnest.Commands.LOL;

import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;

import org.apache.commons.io.IOUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.Utilities.CommandsHandler;
import com.safjnest.Utilities.LOLHandler;

import no.stelar7.api.r4j.impl.R4J;
import no.stelar7.api.r4j.pojo.lol.staticdata.champion.StaticChampion;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @since 1.3
 */
public class Champ extends Command {
    

    /**
     * Constructor
     */
    public Champ(){
        this.name = this.getClass().getSimpleName();
        this.aliases = new CommandsHandler().getArray(this.name, "alias");
        this.help = new CommandsHandler().getString(this.name, "help");
        this.cooldown = new CommandsHandler().getCooldown(this.name);
        this.category = new Category(new CommandsHandler().getString(this.name, "category"));
        this.arguments = new CommandsHandler().getString(this.name, "arguments");
    }

    /**
     * This method is called every time a member executes the command.
     */
	@Override
	protected void execute(CommandEvent event) {

        R4J r = LOLHandler.getRiotApi();
        
        String champ = event.getArgs().split(" ")[0]; //samira
        String lane = event.getArgs().split(" ")[0];

        for(StaticChampion c : r.getDDragonAPI().getChampions().values()){
            if(c.getName().equalsIgnoreCase(champ))
                champ = String.valueOf(c.getId());
        }
        String mainRune = "";
        String secondRune = "";
        ArrayList<String> mainRunes = new ArrayList<String>();
        ArrayList<String> secondRunes = new ArrayList<String>();
        URL url;
        try {
            url = new URL("https://axe.lolalytics.com/mega/?ep=rune&p=d&v=1&cid="+champ+"&lane="+lane);
            String json = IOUtils.toString(url, Charset.forName("UTF-8"));
            int cont = 0;
            url = new URL("https://ddragon.leagueoflegends.com/cdn/13.3.1/data/en_US/runesReforged.json");
            String a = IOUtils.toString(url, Charset.forName("UTF-8"));
            switch(getRunePage(json, "pri")){
                case "0":
                    mainRune = "8000";
                    break;
                case "1":
                    mainRune = "8100";
                    break;

                case "2":
                    mainRune = "8200";
                    break;
                case "3":
                    mainRune = "8400";
                    break;
                case "4":
                    mainRune = "8300";
                    break;
            }
            for(String id : getPrin(json, "pri")){
                mainRunes.add(getRuneName(a, mainRune, id, cont));
                cont+=1;
            }
            mainRune = getMainRuneName(a, mainRune);


            switch(getRunePage(json, "sec")){
                case "0":
                    secondRune = "8000";
                    break;
                case "1":
                    secondRune = "8100";
                    break;

                case "2":
                    secondRune = "8200";
                    break;
                case "3":
                    secondRune = "8400";
                    break;
                case "4":
                    secondRune = "8300";
                    break;
            }

            for(String id : getPrin(json, "sec")){
                cont = 0;
                String awg = null;
                do{
                    awg = getRuneName(a, secondRune, id, cont);
                    cont+=1;
                }while(awg == null);
                if(awg != null){
                    secondRunes.add(awg);
                }
            }
            secondRune = getMainRuneName(a, secondRune);
            String msg = "**" + mainRune + "**\n";
            System.out.println(mainRune);
            for(String s : mainRunes){
                msg+=s+"\n";
            }
            msg+="\n**" + secondRune + "**\n";
            for(String s : secondRunes)
                msg+=s + "\n";
            event.reply(msg);
        } catch (Exception e) {
            e.printStackTrace();
        }
        
	}
    
    public String[] getPrin(String json, String thing){
        JSONParser parser = new JSONParser();
        try {
            
            JSONObject file = (JSONObject) parser.parse(json);
            JSONObject summary = (JSONObject) file.get("summary");
            JSONObject runes = (JSONObject) summary.get("runes");
            JSONObject win = (JSONObject) runes.get("pick");
            JSONObject set = (JSONObject) win.get("set");
            JSONArray theThing = (JSONArray) set.get(thing);
            String[] result = new String[theThing.size()];
            for (int i = 0; i < theThing.size(); i++) {
                result[i] = String.valueOf(theThing.get(i));
            }
            return result;      
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public String getRunePage(String json,String thing){
        JSONParser parser = new JSONParser();
        try {
            
            JSONObject file = (JSONObject) parser.parse(json);
            JSONObject summary = (JSONObject) file.get("summary");
            JSONObject runes = (JSONObject) summary.get("runes");
            JSONObject win = (JSONObject) runes.get("pick");
            JSONObject page = (JSONObject) win.get("page");
            return String.valueOf(page.get(thing));
              
        } catch (Exception e) {
           e.printStackTrace();
           return null;
        }
    }

    public String getMainRuneName(String json, String id){
        JSONParser parser = new JSONParser();
        try {
            
            JSONArray file = (JSONArray) parser.parse(json);
            for(int i = 0; i < 5; i++){
                JSONObject a = (JSONObject)file.get(i);
                if(String.valueOf(a.get("id")).equals(id)){
                    return String.valueOf(a.get("name"));
                }
            }
            return null;
              
        } catch (Exception e) {
           e.printStackTrace();
           return null;
        }
    }

    public String getRuneName(String json, String mainId, String id, int row){
        JSONParser parser = new JSONParser();
        try {
            
            JSONArray file = (JSONArray) parser.parse(json);
            for(int i = 0; i < 5; i++){
                JSONObject a = (JSONObject)file.get(i);
                if(String.valueOf(a.get("id")).equals(mainId)){
                    JSONArray slots = (JSONArray)a.get("slots");
                    JSONObject runes = (JSONObject)slots.get(row);
                    JSONArray rowRune = (JSONArray)runes.get("runes");
                    for(int j = 0; j < rowRune.size(); j++){
                        JSONObject rune = (JSONObject)rowRune.get(j);
                        if(String.valueOf(rune.get("id")).equals(id)){
                            return String.valueOf(rune.get("name"));
                        }
                        
                    }
                }
            }
            return null;
              
        } catch (Exception e) {
           e.printStackTrace();
           return null;
        }
    }

    

}
//TODO: mettere tutti i dati in una mappa che si carica ad ogni avvio, tra cui campioni, rune e altra merda 