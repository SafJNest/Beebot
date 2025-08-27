package com.safjnest.model.guild.alert;

import java.util.HashMap;
import java.util.List;

import com.safjnest.core.Bot;
import com.safjnest.model.guild.GuildData;
import com.safjnest.sql.QueryRecord;
import com.safjnest.sql.database.BotDB;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;

public class AlertData {

    private final int ID;
    private String message;
    private String privateMessage;
    private String channelId;
    private boolean enabled;
    private AlertType type;
    private AlertSendType sendType;
    private HashMap<Integer, String> roles;

    public AlertData(QueryRecord data, HashMap<Integer, String> roles) {
        this.ID = data.getAsInt("alert_id");
        this.message = data.get("message");
        this.privateMessage = data.get("private_message");
        this.channelId = data.get("channel");
        this.enabled = data.getAsBoolean("enabled");
        this.type = AlertType.getFromOrdinal(data.getAsInt("type"));
        if(roles == null) {
            this.roles = new HashMap<Integer, String>();
        }
        else {
            this.roles = roles;
        }
        this.sendType = AlertSendType.getFromOrdinal(data.getAsInt("send_type"));
    }

    public AlertData(int ID, String message, String privateMessage, String channelId, boolean enabled, AlertSendType sendType, AlertType type, HashMap<Integer, String> roles) {
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
        this.ID = BotDB.createAlert(guild_id, message, privateMessage, channelId, sendType, type);
        this.message = message;
        this.privateMessage = privateMessage;
        this.channelId = channelId;
        this.enabled = true;
        this.type = type;
        this.sendType = sendType;
        this.roles = new HashMap<>();
    }

    public AlertData(String guild_id, String message, String privateMessage, String channelId, AlertSendType sendType, String[] roles) {
        this.ID = BotDB.createAlert(guild_id, message, privateMessage, channelId, sendType, AlertType.WELCOME);
        this.message = message;
        this.privateMessage = privateMessage;
        this.channelId = channelId;
        this.enabled = true;
        this.type = AlertType.WELCOME;
        this.sendType = sendType;
        this.roles = BotDB.createRolesAlert(String.valueOf(this.ID), roles);
    }

    public AlertData(String guild_id, String message, String privateMessage, AlertSendType sendType) {
        this.ID = BotDB.createAlert(guild_id, message, privateMessage, null, sendType, AlertType.LEVEL_UP);
        this.message = message;
        this.privateMessage = privateMessage;
        this.channelId = null;
        this.enabled = true;
        this.sendType = sendType;
        this.type = AlertType.LEVEL_UP;
    }

    /**
     * An alert is valid if and only if it has a message, a channel (except for level up messages) and it's enabled (true by default)
     * @return true if the alert is valid, false otherwise
     */
    public boolean isValid() {
        return (hasMessage() || hasPrivateMessage()) && (this.type == AlertType.LEVEL_UP || this.type == AlertType.REWARD ||  this.channelId != null || (this.sendType == AlertSendType.PRIVATE)) && this.enabled;
    }

    public boolean isValid(GuildData guild) {
        boolean isValid = isValid();
        if (this.type == AlertType.LEVEL_UP)
            isValid = guild.isExperienceEnabled() && isValid;
        return isValid;
    }
    
    public boolean setMessage(String message) {
        boolean result = BotDB.setAlertMessage(String.valueOf(this.ID), message);
        if (result) {
            this.message = message;
        }
        return result;
    }

    public boolean setPrivateMessage(String privateMessage) {
        boolean result = BotDB.setAlertPrivateMessage(String.valueOf(this.ID), privateMessage);
        if (result) {
            this.privateMessage = privateMessage;
        }
        return result;
    }

    public boolean setAlertChannel(String channelId) {
        boolean result = BotDB.setAlertChannel(String.valueOf(this.ID), channelId);
        if (result) {
            this.channelId = channelId;
        }
        return result;
    }

    public boolean setEnabled(boolean enabled) {
        boolean result = BotDB.setAlertEnabled(String.valueOf(this.ID), enabled);
        if (result) {
            this.enabled = enabled;
        }
        return result;
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
        HashMap<Integer, String> roles = this.roles;
        if (roles == null) {
            roles = new HashMap<>();
        }
        roles.put(0, roleId);
        this.roles = BotDB.createRolesAlert(String.valueOf(this.ID), roles.values().toArray(new String[0]));
        return this.roles != null;
    }

    public boolean setRoles(List<String> roles) {
        BotDB.deleteAlertRoles(String.valueOf(this.ID));
        this.roles = BotDB.createRolesAlert(String.valueOf(this.ID), roles.toArray(new String[0]));
        return this.roles != null;
    }


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
        HashMap<Integer, String> roles = this.roles;
        if (roles == null || !roles.containsValue(roleId)) {
            return false;
        }
        
        roles.values().removeIf(role -> role.equals(roleId));
        boolean result = false;
        if (roles.isEmpty()) {
            result = BotDB.deleteAlertRoles(String.valueOf(this.ID));
            this.roles = null;
        }
        else {
            this.roles = BotDB.createRolesAlert(String.valueOf(this.ID), roles.values().toArray(new String[0]));
            result = this.roles != null;
        }
        
        return result;
    }

    public boolean terminator4LaRinascita() {
        return BotDB.deleteAlert(String.valueOf(this.ID));
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

    public HashMap<Integer, String> getRoles() {
        return roles;
    }

    public String[] getRolesAsArray() {
        return roles.values().toArray(new String[0]);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public AlertSendType getSendType() {
        return sendType;
    }

    public boolean setSendType(AlertSendType sendType) {
        boolean result = BotDB.alertUpdateSendType(String.valueOf(this.ID), sendType);
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
            for (String role : this.getRoles().values().toArray(new String[0])) {
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
                for (String role : this.getRoles().values().toArray(new String[0])) {
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