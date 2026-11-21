package com.safjnest.bot.model.guild.alert;

import java.util.HashMap;
import java.util.List;
import java.util.Arrays;
import java.util.stream.Collectors;

import com.safjnest.bot.Bot;
import com.safjnest.database.sql.QueryRecord;
import com.safjnest.bot.cache.managers.GuildCache;
import com.safjnest.database.sql.database.BotDB;
import com.safjnest.database.sql.database.BotDB.SavedAlert;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;

public class RewardData extends AlertData{
    private int level;
    private boolean temporary;

    public RewardData(QueryRecord data, HashMap<Integer, String> roles) {
        this(data, roles, BotDB.get());
    }

    public RewardData(QueryRecord data, HashMap<Integer, String> roles, BotDB database) {
        super(data, roles, database);

        this.level = data.getAsInt("level");
        this.temporary = data.getAsBoolean("temporary");
    }
    
    public RewardData(int ID, String message, String privateMessage, boolean enabled, AlertSendType sendType, HashMap<Integer, String> roles, int level, boolean temporary) {
        super(ID, message, privateMessage, null, enabled, sendType, AlertType.REWARD, roles);

        this.level = level;
        this.temporary = temporary;
    }


    private RewardData(RewardData original) {
        super(original);

        this.level = original.level;
        this.temporary = original.temporary;
    }

    public static RewardData createRewardData(String guild_id, String message, String privateMessage, AlertSendType sendType, String[] roles, int level, boolean temporary) {
        return GuildCache.withGuild(guild_id, guild -> (RewardData) guild.createAlert(AlertType.REWARD, String.valueOf(level), edit -> {
            edit.setMessage(message == null ? "" : message);
            edit.setPrivateMessage(privateMessage);
            edit.setSendType(sendType);
            edit.setRoles(roles == null ? List.of() : Arrays.asList(roles));
            ((RewardData) edit).setTemporary(temporary);
        }));
    }

    @Override
    public synchronized RewardData deferEdit() {
        return new RewardData(this);
    }

    public synchronized int getLevel() {
        return level;
    }

    /**
     * A reward is temporary if it is removed when a higher level reward is achieved
     * @return true if the reward is temporary
     */
    public synchronized boolean isTemporary() {
        return temporary;
    }

    public boolean setLevel(int level) {
        if (level < 1 || level > 100)
            return false;

        return change(edit -> ((RewardData) edit).level = level);
    }

    public boolean setTemporary(boolean temporary) {
        return change(edit -> ((RewardData) edit).temporary = temporary);
    }

    @Override
    public synchronized EmbedBuilder getSampleEmbed(Guild guild) {
        List<String> roleNames = this.getRoles().values().stream()
            .map(role -> "@" + guild.getRoleById(role).getName())
            .collect(Collectors.toList());


        String sampleText = super.getMessage();
        sampleText = sampleText.replace("#user", "@sunyx");
        sampleText = sampleText.replace("#level", "117");
        sampleText = sampleText.replace("#role", String.join(", ", roleNames));

        if (super.getPrivateMessage() != null && !super.getPrivateMessage().isEmpty()) {
            String privateSampleText = super.getPrivateMessage();
            privateSampleText = privateSampleText.replace("#user", "@sunyx");
            privateSampleText = privateSampleText.replace("#level", "117");
            privateSampleText = privateSampleText.replace("#role", String.join(", ", roleNames));
            sampleText = "Public message:\n" + sampleText + "\n\nPrivate message:\n" + privateSampleText;
        }

        EmbedBuilder eb = new EmbedBuilder();
        eb.setAuthor(guild.getSelfMember().getEffectiveName(), "https://github.com/SafJNest", guild.getSelfMember().getEffectiveAvatarUrl());
        eb.setTitle(this.getType().getDescription() + "'s preview");
        eb.setDescription("```" + sampleText + "```");
        eb.setColor(Bot.getColor());
        eb.setThumbnail(guild.getSelfMember().getEffectiveAvatarUrl());
        
        eb.addField("Level", "```" + this.level + "```", true);

        eb.addField("is Enabled",
                    (this.isEnabled()
                        ?"```✅ Yes```"
                        :"```❌ No```")
                    , true);
        
        eb.addField("Temporary",
                    (this.temporary
                        ?"```✅ Yes```"
                        :"```❌ No```")
                    , true);

        eb.addField("Send type", "```" + super.getSendType().getName() + "```", true);

        eb.addField("Roles", "```" + String.join("\n", roleNames) + "```", false);

        return eb;
    }


    @Override
    public synchronized AlertKey<Integer> getKey() {
        return new AlertKey<>(AlertType.REWARD, this.level);
    }
    

    /* -------------------------------------------------------------------------- */

    @Override
    protected void copyFrom(AlertData edit, SavedAlert saved) {
        super.copyFrom(edit, saved);
        this.level = ((RewardData) edit).level;
        this.temporary = ((RewardData) edit).temporary;
    }

}
