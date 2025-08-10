package com.safjnest.model.guild.alert;

import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import com.safjnest.core.Bot;
import com.safjnest.sql.DatabaseHandler;
import com.safjnest.sql.QueryRecord;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;

public class RewardData extends AlertData{
    private int level;
    private boolean temporary;

    public RewardData(QueryRecord data, HashMap<Integer, String> roles) {
        super(data, roles);
        this.level = data.getAsInt("level");
        this.temporary = data.getAsBoolean("temporary");
    }
    
    public RewardData(int ID, String message, String privateMessage, boolean enabled, AlertSendType sendType, HashMap<Integer, String> roles, int level, boolean temporary) {
        super(ID, message, privateMessage, null, enabled, sendType, AlertType.REWARD, roles);
        this.level = level;
        this.temporary = temporary;
    }


    public static RewardData createRewardData(String guild_id, String message, String privateMessage, AlertSendType sendType, String[] roles, int level, boolean temporary) {
        int id = DatabaseHandler.createAlert(guild_id, message, privateMessage, null, sendType, AlertType.REWARD);
        HashMap<Integer, String> rolesMap = roles != null ? DatabaseHandler.createRolesAlert(String.valueOf(id), roles) : new HashMap<>();
        DatabaseHandler.createRewardData(String.valueOf(id), level, temporary);
        return new RewardData(id, message, privateMessage, true, sendType, rolesMap, level, temporary);
    }

    public int getLevel() {
        return level;
    }


    /**
     * A reward is temporary if it is removed when a higher level reward is achieved
     * @return true if the reward is temporary
     */
    public boolean isTemporary() {
        return temporary;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public void setTemporary(boolean temporary) {
        this.temporary = temporary;
    }

    @Override
    public EmbedBuilder getSampleEmbed(Guild guild) {
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
    public AlertKey<Integer> getKey() {
        return new AlertKey<>(AlertType.REWARD, this.level);
    }
    

}
