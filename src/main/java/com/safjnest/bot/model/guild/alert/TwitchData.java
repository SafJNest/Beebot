package com.safjnest.bot.model.guild.alert;

import java.util.HashMap;

import com.safjnest.database.sql.QueryRecord;
import com.safjnest.bot.cache.managers.GuildCache;
import com.safjnest.database.sql.database.BotDB;
import com.safjnest.database.sql.database.BotDB.SavedAlert;

public class TwitchData extends AlertData {

    private String streamerId;
    private String roleId;

    public TwitchData(QueryRecord data) {
        this(data, BotDB.get());
    }

    public TwitchData(QueryRecord data, BotDB database) {
        super(data, null, database);

        this.streamerId = data.get("streamer_id");
        this.roleId = data.get("role_id");
    }

    public TwitchData(int ID, String message, String privateMessage, String channel, boolean enabled, AlertSendType sendType, String streamerId, String roleId) {
        super(ID, message, privateMessage, channel, enabled, sendType, AlertType.TWITCH, new HashMap<>());

        this.streamerId = streamerId;
        this.roleId = roleId;
    }


    private TwitchData(TwitchData original) {
        super(original);

        this.streamerId = original.streamerId;
        this.roleId = original.roleId;
    }

    public static TwitchData createTwitchData(String guild_id, String streamerId, String message, String privateMessage, String channel, AlertSendType sendType, String roleId) {
        return GuildCache.withGuild(guild_id, guild -> (TwitchData) guild.createAlert(AlertType.TWITCH, streamerId, edit -> {
            edit.setMessage(message == null ? "" : message);
            edit.setPrivateMessage(privateMessage);
            edit.setAlertChannel(channel);
            edit.setSendType(sendType);
            ((TwitchData) edit).setStreamerRole(roleId);
        }));
    }

    @Override
    public synchronized TwitchData deferEdit() {
        return new TwitchData(this);
    }

    public String getStreamer() {
        return streamerId;
    }

    public synchronized String getStreamerRole() {
        return roleId;
    }

    public boolean setStreamerRole(String roleId) {
        return change(edit -> ((TwitchData) edit).roleId = roleId);
    }

    @Override
    public AlertKey<String> getKey() {
        return new AlertKey<String>(AlertType.TWITCH, this.streamerId);
    }
    
    /* -------------------------------------------------------------------------- */

    @Override
    protected void copyFrom(AlertData edit, SavedAlert saved) {
        super.copyFrom(edit, saved);
        this.roleId = ((TwitchData) edit).roleId;
    }

}
