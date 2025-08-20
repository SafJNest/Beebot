package com.safjnest.model.guild.alert;

<<<<<<< HEAD
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
=======
import java.util.HashMap;
import java.util.List;
>>>>>>> main

import com.safjnest.core.Bot;
import com.safjnest.model.guild.GuildData;
import com.safjnest.sql.DatabaseHandler;
import com.safjnest.sql.QueryRecord;
import com.safjnest.util.SafJNest;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

public class AlertData {

    private final int ID;
    private String message;
    private String privateMessage;
    private String channelId;
    private boolean enabled;
    private AlertType type;
    private AlertSendType sendType;
    private List<String> roles;

    public AlertData(QueryRecord data, List<String> roles) {
        this.ID = data.getAsInt("alert_id");
        this.message = data.get("message");
        this.privateMessage = data.get("private_message");
        this.channelId = data.get("channel");
        this.enabled = data.getAsBoolean("enabled");
        this.type = AlertType.getFromOrdinal(data.getAsInt("type"));
        if(roles == null) {
<<<<<<< HEAD
            this.roles = new ArrayList<String>();
=======
            this.roles = new HashMap<Integer, String>();
>>>>>>> main
        }
        else {
            this.roles = roles;
        }
        this.sendType = AlertSendType.getFromOrdinal(data.getAsInt("send_type"));
    }

    public AlertData(int ID, String message, String privateMessage, String channelId, boolean enabled, AlertSendType sendType, AlertType type, List<String> roles) {
        this.ID = ID;
        this.message = message;
        this.privateMessage = privateMessage;
        this.channelId = channelId;
        this.enabled = enabled;
        this.type = type;
        this.roles = roles;
        this.sendType = sendType;
    }

    public AlertData(String guild_id, String message, String privateMessage, String channelId, AlertSendType sendType, AlertType type) {
        this.ID = DatabaseHandler.createAlert(guild_id, message, privateMessage, channelId, sendType, type);
        this.message = message;
        this.privateMessage = privateMessage;
        this.channelId = channelId;
        this.enabled = true;
        this.type = type;
        this.sendType = sendType;
        this.roles = new HashMap<>();
    }

    public AlertData(String guild_id, String message, String privateMessage, String channelId, AlertSendType sendType, List<String> roles) {
        this.ID = DatabaseHandler.createAlert(guild_id, message, privateMessage, channelId, sendType, AlertType.WELCOME);
        this.message = message;
        this.privateMessage = privateMessage;
        this.channelId = channelId;
        this.enabled = true;
        this.type = AlertType.WELCOME;
        this.sendType = sendType;

        DatabaseHandler.createRolesAlert(String.valueOf(this.ID), roles);
        this.roles = roles;
    }

    public AlertData(String guild_id, String message, String privateMessage, AlertSendType sendType) {
        this.ID = DatabaseHandler.createAlert(guild_id, message, privateMessage, null, sendType, AlertType.LEVEL_UP);
        this.message = message;
        this.privateMessage = privateMessage;
        this.channelId = null;
        this.enabled = true;
        this.sendType = sendType;
        this.type = AlertType.LEVEL_UP;
    }

    public AlertData(String guildId, AlertType alertType, Map<String, Object> data) {
        validateInputs(guildId, alertType, data);
    
        this.type = alertType;
        this.enabled = true;
    
        initializeFromData(guildId, data);
        validateMessageConsistency();
        if(this.sendType == null) {
            inferSendType();
        }

        setSystemChannelIfNull(guildId);
    
        this.ID = DatabaseHandler.createAlert(guildId, message, privateMessage, channelId, sendType, alertType);

        if (this.ID == 0) {
            throw new RuntimeException("Failed to create alert in the database");
        }
    
        if (roles != null && !roles.isEmpty()) {
            DatabaseHandler.createRolesAlert(String.valueOf(this.ID), roles);
        }
    
        if (!this.enabled) {
            DatabaseHandler.setAlertEnabled(String.valueOf(this.ID), false);
        }
    }
    
    private void validateInputs(String guildId, AlertType alertType, Map<String, Object> data) {
        if (guildId == null || guildId.isEmpty()) throw new IllegalArgumentException("Guild ID cannot be null or empty");
        if (alertType == null) throw new IllegalArgumentException("Alert type cannot be null");
        if (data == null || data.isEmpty()) throw new IllegalArgumentException("Data cannot be null or empty");
    }

    private void setSystemChannelIfNull(String guildId) {
        setSystemChannelIfNull(guildId, this);
    }

    private void setSystemChannelIfNull(String guildId, AlertData alert) {
        if(alert.type == AlertType.LEVEL_UP || alert.sendType == AlertSendType.PRIVATE) {
            return;
        }
        if(alert.channelId == null) {
            TextChannel systemChannel = Bot.getJDA().getGuildById(guildId).getSystemChannel();
            if(systemChannel == null) {
                throw new IllegalArgumentException("System channel is null and no channel ID provided");
            }
            alert.channelId = systemChannel.getId();
        }
    }
    
    private void initializeFromData(String guildId, Map<String, Object> data) {
        changeSettings(guildId, this, data);
    }

    private void changeSettings(String guildId, AlertData alert, Map<String, Object> data) {
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
    
            switch (key) {
                case "message" -> alert.message = (String) value;
                case "privateMessage" -> alert.privateMessage = (String) value;
                case "channelId" -> {
                    alert.channelId = (String) value;
                    if (alert.channelId != null && Bot.getJDA().getGuildById(guildId).getTextChannelById(alert.channelId) == null) {
                        throw new IllegalArgumentException("Invalid channel ID: " + alert.channelId);
                    }
                }
                case "enabled" -> alert.enabled = (boolean) value;
                case "sendType" -> alert.sendType = AlertSendType.parse((String) value);
                case "roles" -> alert.roles = SafJNest.getStringList(value, key);
                default -> throw new IllegalArgumentException("Unknown key: " + key);
            }
        }
    }

    private void validateMessageConsistency() {
        validateMessageConsistency(this);
    }
    
    private void validateMessageConsistency(AlertData alert) {
        boolean msgEmpty = alert.message == null || alert.message.isEmpty();
        boolean pmEmpty = alert.privateMessage == null || alert.privateMessage.isEmpty();
    
        if (msgEmpty && pmEmpty)
            throw new IllegalArgumentException("Message and privateMessage cannot both be null or empty");
    
        if (alert.sendType == AlertSendType.BOTH && (msgEmpty || pmEmpty))
            throw new IllegalArgumentException("Both messages cannot be null or empty if sendType is BOTH");
    
        if (alert.sendType == AlertSendType.CHANNEL && msgEmpty)
            throw new IllegalArgumentException("Public message cannot be null or empty if sendType is CHANNEL");
    
        if (alert.sendType == AlertSendType.PRIVATE && pmEmpty)
            throw new IllegalArgumentException("Private message cannot be null or empty if sendType is PRIVATE");
    }

    private boolean isMessageConsistent(AlertData alert) {
        try {
            validateMessageConsistency(alert);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
    
    private void inferSendType() {
        inferSendType(this);
    }

    private void inferSendType(AlertData alert) {
        boolean hasMsg = alert.message != null && !alert.message.isEmpty();
        boolean hasPm = alert.privateMessage != null && !alert.privateMessage.isEmpty();

        if (hasMsg && hasPm) alert.sendType = AlertSendType.BOTH;
        else if (hasMsg) alert.sendType = AlertSendType.CHANNEL;
        else if (hasPm) alert.sendType = AlertSendType.PRIVATE;
    }

    private AlertData cloneState() {
        return new AlertData(
            this.ID,
            this.message,
            this.privateMessage,
            this.channelId,
            this.enabled,
            this.sendType,
            this.type,
            this.roles == null ? null : new ArrayList<String>(this.roles)
        );
    }

    /**
     * An alert is valid if and only if it has a message, a channel (except for level up messages) and it's enabled (true by default)
     * @return true if the alert is valid, false otherwise
     */
    public boolean isValid() {
<<<<<<< HEAD
        return isMessageConsistent(this) 
            && (this.type == AlertType.LEVEL_UP || this.channelId != null || (this.sendType == AlertSendType.PRIVATE)) 
            && this.enabled;
        //the level up check should probably be made in an override method in a subclass
=======
        return (hasMessage() || hasPrivateMessage()) && (this.type == AlertType.LEVEL_UP || this.type == AlertType.REWARD ||  this.channelId != null || (this.sendType == AlertSendType.PRIVATE)) && this.enabled;
    }

    public boolean isValid(GuildData guild) {
        boolean isValid = isValid();
        if (this.type == AlertType.LEVEL_UP)
            isValid = guild.isExperienceEnabled() && isValid;
        return isValid;
>>>>>>> main
    }
    
    public boolean setMessage(String message) {
        boolean result = DatabaseHandler.setAlertMessage(String.valueOf(this.ID), message);
        if (result) {
            this.message = message;
        }
        return result;
    }

    public boolean setPrivateMessage(String privateMessage) {
        boolean result = DatabaseHandler.setAlertPrivateMessage(String.valueOf(this.ID), privateMessage);
        if (result) {
            this.privateMessage = privateMessage;
        }
        return result;
    }

    public boolean setAlertChannel(String channelId) {
        boolean result = DatabaseHandler.setAlertChannel(String.valueOf(this.ID), channelId);
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

    public boolean set(String guildId, Map<String, Object> settings) {
        if (settings == null || settings.isEmpty()) {
            return false;
        }
    
        AlertData newAlert = cloneState();
        changeSettings(guildId, newAlert, settings);
        if(settings.containsKey("sendType")) {
            validateMessageConsistency(newAlert);
        }
        else if(!isMessageConsistent(newAlert)) {
            inferSendType(newAlert);
            validateMessageConsistency(newAlert);
        }

        setSystemChannelIfNull(guildId, newAlert);

        if (!DatabaseHandler.updateAlertandRoles(String.valueOf(this.ID), newAlert)) {
            return false;
        }

        this.message = newAlert.message;
        this.privateMessage = newAlert.privateMessage;
        this.channelId = newAlert.channelId;
        this.sendType = newAlert.sendType;
        this.enabled = newAlert.enabled;
        this.roles = newAlert.roles == null ? null : new ArrayList<String>(newAlert.roles);
    
        return true;
    }

    /**
     * The method first adds the new role to the local roles map at position 0, 
     * because the actual database row ID for the new role is not known yet.
     * 
     * It then calls a database operation that deletes all existing roles for 
     * the alert and re-adds them, including the new role. This operation also 
     * assigns actual database row IDs to the roles.
     * 
     * The updated roles are then assigned back to the local roles map.
     * 
     * @param roleId The ID of the new role to be added.
     * @return true if the operation was successful, false otherwise.
     * 
     */
    public boolean addRole(String roleId) {
        List<String> roles = this.roles;
        if (roles == null) {
            roles = new ArrayList<>();
        }
        roles.add(roleId);
        return DatabaseHandler.createRolesAlert(String.valueOf(this.ID), roles);
    }

<<<<<<< HEAD
=======
    public boolean setRoles(List<String> roles) {
        DatabaseHandler.deleteAlertRoles(String.valueOf(this.ID));
        this.roles = DatabaseHandler.createRolesAlert(String.valueOf(this.ID), roles.toArray(new String[0]));
        return this.roles != null;
    }


>>>>>>> main
    /**
     * The method first removes the role from the local roles map, 
     * using the provided role ID.
     * 
     * It then calls a database operation that deletes all existing roles for 
     * the alert and re-adds them, excluding the removed role. This operation also 
     * assigns actual database row IDs to the roles.
     * 
     * The updated roles are then assigned back to the local roles map.
     * 
     * @param roleId The ID of the role to be removed.
     * @return true if the operation was successful, false otherwise.
     * 
     */
    public boolean removeRole(String roleId) {
        List<String> roles = this.roles;
        if (roles == null || !roles.contains(roleId)) {
            return false;
        }
        
        roles.removeIf(role -> role.equals(roleId));
        boolean result = false;
        if (roles.isEmpty()) {
            result = DatabaseHandler.deleteAlertRoles(String.valueOf(this.ID));
            this.roles = null;
        }
        else {
            result = DatabaseHandler.createRolesAlert(String.valueOf(this.ID), roles);
        }
        
        return result;
    }

    public boolean terminator4LaRinascita() {
        return DatabaseHandler.deleteAlert(String.valueOf(this.ID));
    }

    public int getID() {
        return ID;
    }

    public String getMessage() {
        return message;
    }

    public String getPrivateMessage() {
        return privateMessage;
    }

    public boolean hasPrivateMessage() {
        return this.privateMessage != null && !this.privateMessage.isEmpty();
    }

    public boolean hasMessage() {
        return this.message != null && !this.message.isEmpty();
    }

    public String getChannelId() {
        return channelId;
    }

    public AlertType getType() {
        return type;
    }

    public List<String> getRoles() {
        return roles;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public AlertSendType getSendType() {
        return sendType;
    }

    public boolean setSendType(AlertSendType sendType) {
        boolean result = DatabaseHandler.alertUpdateSendType(String.valueOf(this.ID), sendType);
        if (result) {
            this.sendType = sendType;
        }
        return result;
    }

    public String getFormattedSample(Guild guild) {
        String sampleText = this.message;
        sampleText = sampleText.replace("#user", "@sunyx");
        sampleText = sampleText.replace("#level", "117");

        String channelText = "This alert has not a channel set.";
        if (this.type == AlertType.LEVEL_UP) {
            channelText = "";
        }
        else if (this.getChannelId() != null) {
            channelText = "This message would be sent to: " + guild.getTextChannelById(this.getChannelId()).getAsMention() + "\n";
        }

        String roleText = "";
        if (this.type == AlertType.WELCOME && this.getRoles() != null) {
            roleText += "Roles that would be given to the user:";
            for (String role : this.getRoles()) {
                roleText += "\n" + guild.getRoleById(role).getName();
            }
        }
        return
               "The " + this.type.getDescription() + " is " + (this.enabled ? "enabled" : "disabled") + ":\n"
               + "```"
               + sampleText
               + "```"
               + channelText
               + roleText;
    }

    public EmbedBuilder getSampleEmbed(Guild guild) {
        String sampleText = this.message;
        sampleText = sampleText.replace("#user", "@sunyx");
        sampleText = sampleText.replace("#level", "117");

        if (this.privateMessage != null && !this.privateMessage.isEmpty()) {
            String samplePrivateText = this.privateMessage;
            samplePrivateText = samplePrivateText.replace("#user", "@sunyx");
            samplePrivateText = samplePrivateText.replace("#level", "117");

            sampleText = "Public message:\n" + sampleText + "\n\nPrivate message:\n" + samplePrivateText;
        }

        EmbedBuilder eb = new EmbedBuilder();
        eb.setAuthor(guild.getSelfMember().getEffectiveName(), "https://github.com/SafJNest", guild.getSelfMember().getEffectiveAvatarUrl());
        eb.setTitle(this.getType().getDescription() + "'s preview");
        eb.setDescription("```" + sampleText + "```");
        eb.setColor(Bot.getColor());
        eb.setThumbnail(guild.getSelfMember().getEffectiveAvatarUrl());

        String channelText = "This alert has not a channel set.";
        if (this.type == AlertType.LEVEL_UP) {
            channelText = "Level up has not a channel";
        }
        else if (this.getChannelId() != null) {
            channelText = guild.getTextChannelById(this.getChannelId()).getName();
        }
        eb.addField("Channel", "```" + channelText + "```", true);

        eb.addField("is Enabled",
                    (this.isEnabled()
                        ?"```✅ Yes```"
                        :"```❌ No```")
                    , true);
        
        eb.addField("Send type", "```" + this.sendType.getName() + "```", true);
        

        if (this.type == AlertType.WELCOME) {
            if (this.getRoles() == null) {
                eb.addField("Roles", "```Zero roles setted.```", false);
            }
            else {
                String roleText = "```";
                for (String role : this.getRoles()) {
                    roleText += guild.getRoleById(role).getName() + "\n";
                }
                roleText += "```";
                eb.addField("Roles", roleText, false);
            }
        }


        return eb;
    }


    public AlertKey<?> getKey() {
        return new AlertKey<>(this.type);
    }

    @Override
    public String toString() {
        return "AlertData [ID=" + ID + ", channelId=" + channelId + ", enabled=" + enabled + ", message=" + message + ", privateMessage=" + privateMessage
                + ", roles=" + roles + ", type=" + type + "]";
    }

    public RewardData asReward() {
        return (RewardData) this;
    }

    
}