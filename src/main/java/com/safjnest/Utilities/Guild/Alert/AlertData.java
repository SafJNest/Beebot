package com.safjnest.Utilities.Guild.Alert;

import java.util.HashMap;

import com.safjnest.Utilities.SQL.DatabaseHandler;

public class AlertData {

    private final int ID;
    private String message;
    private String channelId;
    private boolean enabled;
    private AlertType type;
    private HashMap<Integer, String> roles;

    public AlertData(int ID, String message, String channelId, boolean enabled, AlertType type, HashMap<Integer, String> roles) {
        this.ID = ID;
        this.message = message;
        this.channelId = channelId;
        this.enabled = enabled;
        this.type = type;
        this.roles = roles;
    }
    
    public boolean setMessage(String message) {
        boolean result = DatabaseHandler.setAlertMessage(String.valueOf(this.ID), message);
        if (result) {
            this.message = message;
        }
        return result;
    }

    public boolean setAlertChannel(String channelId) {
        boolean result = DatabaseHandler.setAlertMessage(String.valueOf(this.ID), channelId);
        if (result) {
            this.channelId = channelId;
        }
        return result;
    }

    public boolean setEnabled(boolean enabled) {
        boolean result = DatabaseHandler.setAlertEnabled(String.valueOf(this.ID), enabled);
        if (result) {
            this.enabled = enabled;
        }
        return result;
    }



    public int getID() {
        return ID;
    }

    public String getMessage() {
        return message;
    }

    public String getChannelId() {
        return channelId;
    }

    public AlertType getType() {
        return type;
    }

    public HashMap<Integer, String> getRoles() {
        return roles;
    }

    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public String toString() {
        return "AlertData [ID=" + ID + ", channelId=" + channelId + ", enabled=" + enabled + ", message=" + message
                + ", roles=" + roles + ", type=" + type + "]";
    }

    
}