package com.safjnest.Utilities;

import java.util.ArrayList;
import java.util.HashMap;

import com.safjnest.App;

public class GuildSettings {
    public static HashMap<String, GuildData> cache = new HashMap<>();
    final GuildData data;

    public GuildSettings(GuildData input) {
        data = input;
    }

    public static GuildData getServer(String id) {
        if(cache.containsKey(id)) {
            return cache.get(id);
        } else {
            return retrieveServer(id);
        }
    }

    public static GuildData getServerIfCached(String id) {
        return cache.get(id);
    }

    public static GuildData retrieveServer(String stringId) {
        String query = "SELECT * FROM guild_settings WHERE guild_id = '" + stringId + "' AND bot_id = '" + App.botId + "';";
        ArrayList<String> guildArrayList = DatabaseHandler.getSql().getRealTuple(query, 0);
        GuildData guild = (guildArrayList == null) 
                    ? new GuildData(Long.parseLong(stringId), App.PREFIX) 
                    : new GuildData(Long.parseLong(guildArrayList.get(0)), guildArrayList.get(2));
        saveData(guild); //pls be synchronzied
        return guild;
    }

    public static void saveData(GuildData guild) {
        cache.put(String.valueOf(guild.getId()), guild);
    }

    public String getId() {
        return data.getId().toString();
    }

    public String getPrefix() {
        return data.getPrefix();
    }
}
