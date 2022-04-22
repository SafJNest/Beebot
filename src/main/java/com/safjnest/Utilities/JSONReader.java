package com.safjnest.Utilities;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class JSONReader {
    private String path = "rsc" + File.separator +"commands.json";
    private FileReader reader;
    private JSONParser jsonParser;

    public JSONReader(){
        jsonParser = new JSONParser();
        try {
            reader = new FileReader(path);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    public String getString(String nameCommand, String thing){
        nameCommand = nameCommand.toLowerCase();
        thing = thing.toLowerCase();
        Object obj;
        try {
            obj = jsonParser.parse(reader);
            JSONObject commands = (JSONObject) obj;
            JSONObject command = (JSONObject) commands.get(nameCommand);
            return (String) command.get(thing);
        } catch (Exception e) {
            return null;
        }
    }
    public String[] getArray(String nameCommand, String thing){
        nameCommand = nameCommand.toLowerCase();
        thing = thing.toLowerCase();
        Object obj;
        try {
            obj = jsonParser.parse(reader);
            JSONObject commands = (JSONObject) obj;
            JSONObject command = (JSONObject) commands.get(nameCommand);
            JSONArray array = (JSONArray)command.get(thing);
            String[] result = new String[array.size()];
            for (int i = 0; i < array.size(); i++) {
                result[i] = (String)array.get(i);
            }
            return result;
        } catch (IOException | ParseException e) {
            return null;
        }
    }
    
    public int getCooldown(String nameCommand){
        nameCommand = nameCommand.toLowerCase();
        Object obj;
        try {
            obj = jsonParser.parse(reader);
            JSONObject commands = (JSONObject) obj;
            JSONObject command = (JSONObject) commands.get(nameCommand);
            return (command.get("cooldown") == null) ? 0 : Integer.valueOf((String)command.get("cooldown"));
        } catch (IOException | ParseException e) {
            return 0;
        }
    }
}
