package com.safjnest.bot.model.guild.alert;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Consumer;

import com.safjnest.bot.Bot;
import com.safjnest.bot.model.guild.GuildData;
import com.safjnest.bot.model.guild.GuildData.AlertState;
import com.safjnest.database.sql.QueryRecord;
import com.safjnest.database.sql.database.BotDB;
import com.safjnest.database.sql.database.BotDB.SavedAlert;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;

public class AlertData {

    private int ID;
    private final String guildId;

    private final BotDB database;
    private String defaultPrefix = "!";

    private String message;
    private String privateMessage;

    private String channelId;

    private boolean enabled;
    private final AlertType type;
    private AlertSendType sendType;

    private HashMap<Integer, String> roles;

    private AlertData original;
    private boolean deferred;
    private boolean deleted;
    private long revision;

    public AlertData(QueryRecord data, HashMap<Integer, String> roles) {
        this(data, roles, BotDB.get());
    }

    public AlertData(QueryRecord data, HashMap<Integer, String> roles, BotDB database) {
        this.ID = data.getAsInt("alert_id");
        this.guildId = data.get("guild_id");
        this.database = database;

        this.message = data.get("message");
        this.privateMessage = data.get("private_message");

        this.channelId = data.get("channel");

        this.enabled = data.getAsBoolean("enabled");
        this.type = AlertType.getFromOrdinal(data.getAsInt("type"));
        this.sendType = AlertSendType.getFromOrdinal(data.getAsInt("send_type"));

        this.roles = roles == null ? new LinkedHashMap<>() : new LinkedHashMap<>(roles);
    }

    public AlertData(int ID, String message, String privateMessage, String channelId, boolean enabled, AlertSendType sendType, AlertType type, HashMap<Integer, String> roles) {
        this.ID = ID;
        this.guildId = null;
        this.database = BotDB.get();

        this.message = message;
        this.privateMessage = privateMessage;

        this.channelId = channelId;

        this.enabled = enabled;
        this.type = type;

        this.roles = roles == null ? null : new LinkedHashMap<>(roles);
        this.sendType = sendType;
    }

    protected AlertData(AlertData original) {
        if (original.deferred)
            throw new IllegalStateException("This alert is already an edit");

        this.ID = original.ID;
        this.guildId = original.guildId;
        this.database = original.database;
        this.defaultPrefix = original.defaultPrefix;

        this.message = original.message;
        this.privateMessage = original.privateMessage;

        this.channelId = original.channelId;

        this.enabled = original.enabled;
        this.type = original.type;

        this.roles = original.roles == null ? null : new LinkedHashMap<>(original.roles);
        this.sendType = original.sendType;

        this.revision = original.revision;
        this.original = original;
        this.deferred = true;
    }

    public static AlertData draft(String guildId, AlertType type, String key, String defaultPrefix, BotDB database) {
        QueryRecord row = new QueryRecord();
        row.put("guild_id", guildId);
        row.put("message", "");
        row.put("private_message", "");
        row.put("enabled", true);
        row.put("send_type", AlertSendType.CHANNEL.ordinal());
        row.put("type", type.ordinal());

        AlertData draft = switch (type) {
            case TWITCH -> {
                row.put("streamer_id", key);

                yield new TwitchData(row, database);
            }
            case REWARD -> {
                row.put("level", Integer.parseInt(key));

                yield new RewardData(row, null, database);
            }
            default -> new AlertData(row, null, database);
        };

        draft.defaultPrefix = defaultPrefix;
        draft.deferred = true;

        return draft;
    }

    public synchronized AlertData deferEdit() {
        if (deferred)
            throw new IllegalStateException("This alert is already an edit");

        return new AlertData(this);
    }

    public boolean update() {
        if (!deferred)
            return deferEdit().update();

        AlertData target = original == null ? this : original;

        synchronized (target) {
            if (deleted || target.deleted || revision != target.revision)
                return false;

            try {
                SavedAlert saved = database.saveAlert(this);

                target.copyFrom(this, saved);
                target.revision++;

                if (target == this)
                    deferred = false;

                return true;
            } catch (IllegalStateException exception) {
                return false;
            }
        }
    }

    public synchronized boolean isDeleted() {
        return deleted;
    }

    public synchronized boolean isValid() {
        return (hasMessage() || hasPrivateMessage()) && hasRequiredChannel() && enabled;
    }

    public synchronized List<AlertProblem> getProblems(boolean experienceEnabled) {
        List<AlertProblem> problems = new ArrayList<>();

        if (!hasMessage() && !hasPrivateMessage())
            problems.add(new AlertProblem("missing_message", "message", "Manca un messaggio pubblico o privato"));

        if (!hasRequiredChannel())
            problems.add(new AlertProblem("missing_channel", "channelId", "Manca il canale"));

        if (!enabled)
            problems.add(new AlertProblem("disabled", "enabled", "Alert disabilitato"));

        if (type == AlertType.LEVEL_UP && !experienceEnabled)
            problems.add(new AlertProblem("experience_disabled", "experienceEnabled", "Esperienza disabilitata per la guild"));

        return problems;
    }

    public boolean isValid(GuildData guild) {
        boolean experienceEnabled = guild.isExperienceEnabled();

        synchronized (this) {
            return isValid() && (type != AlertType.LEVEL_UP || experienceEnabled);
        }
    }

    public synchronized AlertState snapshot(boolean experienceEnabled) {
        List<AlertProblem> problems = getProblems(experienceEnabled);

        return new AlertState(ID, true, enabled, problems.isEmpty(), message, privateMessage, channelId,
            sendType.name(), List.of(getRolesAsArray()),
            this instanceof TwitchData twitch ? twitch.getStreamer() : null,
            this instanceof TwitchData twitch ? twitch.getStreamerRole() : null,
            this instanceof RewardData reward ? reward.getLevel() : null,
            this instanceof RewardData reward ? reward.isTemporary() : null, List.copyOf(problems));
    }

    public boolean setMessage(String message) {
        return change(edit -> edit.message = message);
    }

    public boolean setPrivateMessage(String privateMessage) {
        return change(edit -> edit.privateMessage = privateMessage);
    }

    public boolean setAlertChannel(String channelId) {
        return change(edit -> edit.channelId = channelId);
    }

    public boolean setEnabled(boolean enabled) {
        return change(edit -> edit.enabled = enabled);
    }

    public synchronized boolean addRole(String roleId) {
        LinkedHashSet<String> updated = new LinkedHashSet<>(List.of(getRolesAsArray()));
        updated.add(roleId);

        return setRoles(new ArrayList<>(updated));
    }

    public boolean setRoles(List<String> roles) {
        return change(edit -> {
            edit.roles = new LinkedHashMap<>();
            int index = 0;

            for (String role : new LinkedHashSet<>(roles))
                edit.roles.put(index++, role);
        });
    }

    public synchronized boolean removeRole(String roleId) {
        List<String> updated = new ArrayList<>(List.of(getRolesAsArray()));

        if (!updated.removeIf(roleId::equals))
            return false;

        return change(edit -> {
            edit.roles = updated.isEmpty() ? null : new LinkedHashMap<>();
            int index = 0;

            for (String role : updated)
                edit.roles.put(index++, role);
        });
    }

    public synchronized boolean terminator4LaRinascita() {
        if (deferred || deleted)
            return false;

        try {
            if (!database.deleteGuildAlert(guildId, ID, defaultPrefix))
                return false;

            deleted = true;
            revision++;

            return true;
        } catch (IllegalStateException exception) {
            return false;
        }
    }

    public synchronized int getID() {
        return ID;
    }

    public String getGuildId() {
        return guildId;
    }

    public String getDefaultPrefix() {
        return defaultPrefix;
    }

    public synchronized String getMessage() {
        return message;
    }

    public synchronized String getPrivateMessage() {
        return privateMessage;
    }

    public synchronized boolean hasPrivateMessage() {
        return privateMessage != null && !privateMessage.isEmpty();
    }

    public synchronized boolean hasMessage() {
        return message != null && !message.isEmpty();
    }

    public synchronized String getChannelId() {
        return channelId;
    }

    public AlertType getType() {
        return type;
    }

    public synchronized HashMap<Integer, String> getRoles() {
        return roles == null ? null : new LinkedHashMap<>(roles);
    }

    public synchronized String[] getRolesAsArray() {
        return roles == null ? new String[0] : roles.values().toArray(new String[0]);
    }

    public synchronized boolean isEnabled() {
        return enabled;
    }

    public synchronized AlertSendType getSendType() {
        return sendType;
    }

    public boolean setSendType(AlertSendType sendType) {
        return change(edit -> edit.sendType = sendType);
    }

    public String getFormattedSample(Guild guild) {
        synchronized (this) {
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
    }

    public EmbedBuilder getSampleEmbed(Guild guild) {
        synchronized (this) {
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
    }


    public AlertKey<?> getKey() {
        return new AlertKey<>(type);
    }

    @Override
    public synchronized String toString() {
        return "AlertData [ID=" + ID + ", channelId=" + channelId + ", enabled=" + enabled + ", message=" + message + ", privateMessage=" + privateMessage
            + ", roles=" + roles + ", type=" + type + "]";
    }

    public RewardData asReward() {
        return (RewardData) this;
    }

    /* -------------------------------------------------------------------------- */

    protected void copyFrom(AlertData edit, SavedAlert saved) {
        this.ID = saved.id();

        this.message = edit.message;
        this.privateMessage = edit.privateMessage;

        this.channelId = edit.channelId;

        this.enabled = edit.enabled;
        this.sendType = edit.sendType;

        this.roles = edit.roles == null ? null : new LinkedHashMap<>(saved.roles());
    }

    protected final synchronized boolean change(Consumer<AlertData> mutation) {
        if (deferred) {
            mutation.accept(this);

            return true;
        }

        AlertData edit = deferEdit();
        mutation.accept(edit);

        return edit.update();
    }

    private boolean hasRequiredChannel() {
        return type == AlertType.LEVEL_UP || type == AlertType.REWARD
            || channelId != null || sendType == AlertSendType.PRIVATE;
    }
}
