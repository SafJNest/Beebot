package com.safjnest.util;

import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

/**
 * Contains the methods to read the JSON file with all the commands descriptions.
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * @since 1.3
 */
public class CommandsLoader {
    private static String path = "rsc" + File.separator + "commands.json";
    private static HashMap<String, BotCommand> commands = new HashMap<>();
 
    static {
        try {
            FileReader reader = new FileReader(path);
            JSONParser jsonParser = new JSONParser();
            JSONObject commandsJson = (JSONObject) jsonParser.parse(reader);
            registerAllCommands(commandsJson);
        } catch (Exception e) { 
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    private static void registerAllCommands(JSONObject commandsJson) {
        if (commandsJson != null) {
            commandsJson.forEach((key, value) -> {
                JSONObject command = (JSONObject) value;
                String name = (String) key;
                String category = (String) command.get("category");
                String help = (String) command.get("help");
                String longHelp = (String) command.get("longhelp");
                longHelp = (longHelp == null || longHelp.isBlank()) ? help : longHelp;
                String arguments = (String) command.get("arguments");
                JSONArray aliasArray = (JSONArray) command.get("alias");
                String[] alias = aliasArray != null ? (String[]) aliasArray.toArray(new String[0]) : new String[0];
                int cooldown = (command.get("cooldown") == null) ? 0 : Integer.valueOf((String) command.get("cooldown"));
                commands.put(name, new BotCommand(name, category, help, longHelp, arguments, alias, cooldown));

                if (command.get("children") != null) {
                    JSONObject children = (JSONObject) command.get("children");
                    children.forEach((childKey, childValue) -> {
                        JSONObject child = (JSONObject) childValue;
                        String childName = (String) childKey;
                        String childHelp = (String) child.get("help");
                        String childLongHelp = (String) child.get("longhelp");
                        childLongHelp = (childLongHelp == null || childLongHelp.isBlank()) ? childHelp : childLongHelp;
                        int childCooldown = (child.get("cooldown") == null) ? 0 : Integer.valueOf((String) child.get("cooldown"));
                        commands.get(name).addChild(new BotCommand(childName, category, childHelp, childLongHelp, null, null, childCooldown, commands.get(name)));
                    });
                }
            });
        }
    } 

    public static BotCommand getCommand(String name) {
        name = name.toLowerCase();
        if(commands.containsKey(name))
            return commands.get(name);
        else {
            return new BotCommand(name, "unknown", "unknown", "unknown", "unknown", null, 0);
        }
    }
    
    public static HashMap<String, BotCommand> getCommandsData(String userId) {
        HashMap<String, BotCommand> filteredCommandsMAP = new HashMap<>();

        for (BotCommand command : commands.values()) {
            if (command.isPresent() && (!command.isHidden() || PermissionHandler.isUntouchable(userId))) {
                filteredCommandsMAP.put(command.getName(), command);
            }
        }

        return filteredCommandsMAP;
    }

    public static List<String> getAllCommandsNames(String userId) {
        List<String> filteredCommandsLIST = new ArrayList<>();


        for (BotCommand command : commands.values()) {
            if (command.isPresent() && (!command.isHidden() || PermissionHandler.isUntouchable(userId))) {
                filteredCommandsLIST.add(command.getName());

                for (BotCommand child : command.getChildren()) {
                    if (child.isPresent() && (!child.isHidden() || PermissionHandler.isUntouchable(userId))) {
                        filteredCommandsLIST.add(command.getName() + " " + child.getName());
                    }
                }
            }
        }

        return filteredCommandsLIST;
    }
}
