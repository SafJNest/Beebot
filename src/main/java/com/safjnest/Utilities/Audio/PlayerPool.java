package com.safjnest.Utilities.Audio;

import java.util.HashMap;
import java.util.Map;

public class PlayerPool {
    private static Map<String, PlayerManager> players = new HashMap<String, PlayerManager>();

    public static Map<String, PlayerManager> getPlayers() {
        return players;
    }

    public static boolean contains(String botId, String guildId) {
        return players.containsKey(botId+guildId);
    }

    public static PlayerManager get(String botId, String guildId) {
        return players.get(botId+guildId);
    }

    public static PlayerManager createPlayer(String botId, String guildId) {
        PlayerManager pm = new PlayerManager(botId, guildId);
        players.put(botId+guildId, pm);
        return pm;
    }

    public static void remove(String botId, String guildId) {
        players.remove(botId+guildId);
    }

    public static void clearPool() {
        players.clear();
    }

    public static String printPlayers() {
        StringBuilder sb = new StringBuilder("{ ");
        for (Map.Entry<String, PlayerManager> entry : players.entrySet())
            sb.append(entry.getKey()).append("=").append(entry.getValue()).append(", ");
        sb.setLength(Math.max(sb.length() - 2, 0));
        sb.append("}");
        return sb.toString();
    }
}