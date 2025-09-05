package com.safjnest.model.guild.alert;

import java.util.HashMap;

import com.safjnest.sql.QueryRecord;
import com.safjnest.sql.database.BotDB;

public class TwitchData extends AlertData {

    private String streamerId;
    private String roleId;

    public TwitchData(QueryRecord data) {
        super(data, null);
        this.streamerId = data.get("streamer_id");
        this.roleId = data.get("role_id");
    }

    public TwitchData(int ID, String message, String privateMessage, String channel, boolean enabled, AlertSendType sendType, String streamerId, String roleId) {
        super(ID, message, privateMessage, channel, enabled, sendType, AlertType.REWARD, new HashMap<>());
        this.streamerId = streamerId;
        this.roleId = roleId;
    }



    public static TwitchData createTwitchData(String guild_id, String streamerId, String message, String privateMessage, String channel, AlertSendType sendType, String roleId) {
        int id = BotDB.createAlert(guild_id, message, privateMessage, channel, sendType, AlertType.TWITCH);
        BotDB.createTwitchData(String.valueOf(id), streamerId, roleId);
        return new TwitchData(id, message, privateMessage, channel, true, sendType, streamerId, roleId);
    }

    public String getStreamer() {
        return streamerId;
    }

    public String getStreamerRole() {
        return roleId;
    }

    public boolean setStreamerRole(String roleId) {
        boolean result = BotDB.updateTwitchRole(this.getID(), roleId);
        if (result) {
            this.roleId = roleId;
        }
        return result;
    }


    @Override
    public AlertKey<String> getKey() {
        return new AlertKey<String>(AlertType.TWITCH, this.streamerId);
    }
    
}
