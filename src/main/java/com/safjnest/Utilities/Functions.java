package com.safjnest.Utilities;

import com.jagrosh.jdautilities.command.CommandClient;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.Bot;

import com.safjnest.Utilities.Guild.GuildData;
import com.safjnest.Utilities.Guild.MemberData;
import com.safjnest.Utilities.Guild.Alert.AlertData;
import com.safjnest.Utilities.Guild.Alert.AlertType;
import com.safjnest.Utilities.Guild.Alert.RewardData;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public class Functions {
    
    public static void handleAlias(GuildData guildData, UserData userData, MessageReceivedEvent e) {
        String prefix = guildData.getPrefix();
        Message message = e.getMessage();
        //BeeMessage newMessage = newBeeMessage(message, newContent);
        if (!message.getContentRaw().startsWith(prefix))
            return;
        
        String command = message.getContentRaw().substring(prefix.length());

        AliasData alias = userData.getAlias(command);
        if (alias == null)
            return;
        
        MessageReceivedEvent eventino = new MessageReceivedEvent(e.getJDA(), (long)0, message);
        
        CommandClient client = Bot.getClient();
        CommandEvent newEvent = new CommandEvent(eventino, prefix, alias.getArgs(), client);
        
        client.getCommands()
            .stream()
            .filter(cmd -> cmd.getName().equals(alias.getBaseCommand()))
            .findFirst()
            .ifPresent(cmd -> cmd.run(newEvent));
    }

    
    public static void handleExperience(GuildData guildData, MessageReceivedEvent e) {
        TextChannel channel = e.getChannel().asTextChannel();
        Guild guild = e.getGuild();
        User newGuy = e.getAuthor();

        if (!guildData.isExpSystemEnabled())
            return;	

        if(!guildData.getExpSystemRoom(e.getChannel().getIdLong()))
            return;

        double modifier = guildData.getExpValueRoom(channel.getIdLong());
        
        MemberData member = guildData.getUserData(newGuy.getIdLong());
        if (!member.canReceiveExperience()) {
            return;
        }

        int exp = member.getExperience();
        int currentLevel = member.getLevel();
        exp = ExperienceSystem.calculateExp(exp, modifier);
        int lvl = ExperienceSystem.isLevelUp(exp, currentLevel);

        if(lvl == ExperienceSystem.NOT_LEVELED_UP) {
            member.setExpData(exp, currentLevel);
            return;
        }
        member.setExpData(exp, lvl);


        RewardData reward = guildData.getAlert(AlertType.REWARD, lvl);
        if (reward != null && !reward.isValid()) {
            String message = reward.getMessage();
            String[] roles = reward.getRolesAsArray();
            message = message.replace("#user", newGuy.getAsMention());
            message = message.replace("#level", String.valueOf(lvl));
            //message = message.replace("#role", role.getName());

            final String finalMessage = message;
            if (reward.isPrivate()) {
                newGuy.openPrivateChannel().queue(channelPrivate -> {
                    channelPrivate.sendMessage(finalMessage).queue();
                });
            } else {
                channel.sendMessage(finalMessage).queue();
            }
            
            for (String roleID : roles) {
                Role role = guild.getRoleById(roleID);
                if (role == null)
                    continue;
                guild.addRoleToMember(UserSnowflake.fromId(newGuy.getId()), role).queue();
            }

            RewardData toDelete = null;
            if ((toDelete = guildData.getLowerReward(lvl)) != null && toDelete.isTemporary()) {
                roles = toDelete.getRolesAsArray();
                for (String roleID : roles) {
                    Role role = guild.getRoleById(roleID);
                    if (role == null)
                        continue;
                    guild.removeRoleFromMember(UserSnowflake.fromId(newGuy.getId()), role).queue();
                }
            }
            return;
        }
            
        AlertData alert = guildData.getAlert(AlertType.LEVEL_UP);
        if (alert != null && alert.isValid()) {
            String message = alert.getMessage();
            message = message.replace("#user", newGuy.getAsMention());
            message = message.replace("#level", String.valueOf(lvl));
            e.getChannel().asTextChannel().sendMessage(message).queue();
            return;
        }
    }


}
