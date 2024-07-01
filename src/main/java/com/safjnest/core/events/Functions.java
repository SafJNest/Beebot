package com.safjnest.core.events;

import java.io.File;
import java.util.HashMap;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandClient;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.sql.DatabaseHandler;
import com.safjnest.sql.QueryResult;
import com.safjnest.sql.ResultRow;
import com.safjnest.util.ExperienceSystem;
import com.safjnest.core.Bot;
import com.safjnest.core.audio.PlayerManager;
import com.safjnest.core.audio.TrackData;
import com.safjnest.core.audio.types.AudioType;
import com.safjnest.model.AliasData;
import com.safjnest.model.UserData;
import com.safjnest.model.guild.BlacklistData;
import com.safjnest.model.guild.alert.AlertType;
import com.safjnest.model.guild.alert.RewardData;
import com.safjnest.model.guild.customcommand.Task;
import com.safjnest.model.guild.customcommand.CustomCommand;
import com.safjnest.model.guild.alert.AlertData;
import com.safjnest.model.guild.alert.AlertKey;
import com.safjnest.model.guild.MemberData;
import com.safjnest.model.guild.GuildData;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;

public class Functions {
    
    public static void handleAlias(GuildData guildData, UserData userData, MessageReceivedEvent e) {
        String prefix = guildData.getPrefix();
        Message message = e.getMessage();
        //BeeMessage newMessage = newBeeMessage(message, newContent);
        if (!message.getContentRaw().startsWith(prefix))
            return;
        
        String command = message.getContentRaw().substring(prefix.length());

        AliasData alias = userData.getAliases().get(command);
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

    public static void handleAlert(User theGuy, Guild guild, AlertType type) {
        MessageChannel channel = null;

        AlertData alert = Bot.getGuildSettings().getGuild(guild.getId()).getAlert(type);
        if (alert == null || !alert.isValid()) {
            return;
        }

        String channel_id = alert.getChannelId();
        if (channel_id == null || channel_id.equals("") || channel_id.equals("null")) {
            return;
        }

        channel = guild.getTextChannelById(channel_id);
        if (channel == null) {
            return;
        }

        String message = alert.getMessage().replace("#user", theGuy.getAsMention());
        if (alert.isPrivate()) {
            theGuy.openPrivateChannel().queue(channelPrivate -> {
                channelPrivate.sendMessage(message).queue();
            });
        } else {
            channel.sendMessage(message).queue();
        }

        if (alert.getRoles() != null) {
            String[] roles = alert.getRoles().values().toArray(new String[0]);
            givesRoles(theGuy, guild, roles);
        }
    }


    public static void givesRoles(User theGuy, Guild guild, String[] roles) {
        if (roles == null || roles.length == 0) {
            return;
        }
        for (String role : roles) {
            Role r = guild.getRoleById(role);
            if (r == null) {
                continue;
            }

            guild.addRoleToMember(theGuy, r).queue();
        }
    }

    public static void handleBlacklist(User badGuy, Guild guild) {
        MessageChannel channel = null;
        GuildData guildData = Bot.getGuildSettings().getGuild(guild.getId());


        int threshold = guildData.getThreshold();
        if(threshold == 0)
            return;
        
        int times = DatabaseHandler.getBlacklistBan(badGuy.getId());

        if(!guildData.blacklistEnabled() || guildData.getThreshold() == 0 || guildData.getThreshold() > times)
            return;
        
        EmbedBuilder eb = new EmbedBuilder();
        eb.setAuthor(guild.getJDA().getSelfUser().getName());
        eb.setThumbnail(badGuy.getAvatarUrl());
        eb.setTitle(":radioactive:Blacklist:radioactive:");
        eb.setDescription("The new member " + badGuy.getAsMention() + " is on the blacklist for being banned in " + times + " different guilds.\nYou have the discretion to choose the next steps.");

        channel = guild.getTextChannelById(guildData.getBlackChannelId());

        Button kick = Button.primary("kick-" + badGuy.getId(), "Kick");
        Button ban = Button.primary("ban-" + badGuy.getId(), "Ban");
        Button ignore = Button.primary("ignore-" + badGuy.getId(), "Ignore");


        kick = kick.withStyle(ButtonStyle.PRIMARY);
        ban = ban.withStyle(ButtonStyle.PRIMARY);
        ignore = ignore.withStyle(ButtonStyle.SUCCESS);
        channel.sendMessageEmbeds(eb.build()).addActionRow(ignore, kick, ban).queue();
    }

    public static void handleBlacklistAlert(User badGuy, Guild guild) {
        GuildData guildData = Bot.getGuildSettings().getGuild(guild.getId());
        int threshold = guildData.getThreshold();

        if(threshold == 0)
            return;
        
        DatabaseHandler.insertUserBlacklist(badGuy.getId(), guild.getId());

        int times = 0;
        times = times + DatabaseHandler.getBannedTimes(badGuy.getId());

        QueryResult guilds = DatabaseHandler.getGuildByThreshold(times, guild.getId());
        if(guilds == null)
            return;
        
        EmbedBuilder eb = new EmbedBuilder();
        eb.setAuthor(guild.getJDA().getSelfUser().getName());
        eb.setThumbnail(badGuy.getAvatarUrl());
        eb.setTitle(":radioactive:Blacklist:radioactive:");
        eb.setDescription("The member " + badGuy.getAsMention() + " is on the blacklist for being banned in " + times + " different guilds.\nYou have the discretion to choose the next steps.");
        for(ResultRow g : guilds){
            Guild gg = guild.getJDA().getGuildById(g.get("guild_id"));
            if(gg.getMemberById(badGuy.getId()) == null)
                continue;

            TextChannel channel = gg.getTextChannelById(g.get("blacklist_channel"));

            Button kick = Button.primary("kick-" + badGuy.getId(), "Kick");
            Button ban = Button.primary("ban-" + badGuy.getId(), "Ban");
            Button ignore = Button.primary("ignore-" + badGuy.getId(), "Ignore");

            kick = kick.withStyle(ButtonStyle.PRIMARY);
            ban = ban.withStyle(ButtonStyle.PRIMARY);
            ignore = ignore.withStyle(ButtonStyle.SUCCESS);
            channel.sendMessageEmbeds(eb.build()).addActionRow(ignore, kick, ban).queue();
        }
    }

    public static void handleChannelDeleteAlert(Guild guild, String channelID) {
        GuildData g = Bot.getGuildSettings().getGuild(guild.getId());

        String alertChannel = guild.getDefaultChannel().getId();
        String alertMessage = "";
        String content = "";
        HashMap<AlertKey, AlertData> alerts = g.getAlerts();
        BlacklistData bld = g.getBlacklistData();
        if (alerts != null) {
            for (AlertData data : alerts.values()) {
                if (data.getChannelId() != null && data.getChannelId().equals(channelID)) {
                    data.setAlertChannel(null);
                    content += data.getType().getDescription() + ", ";
                }
            }
        }
        if (bld != null) {
            if (bld.getBlackChannelId() != null && bld.getBlackChannelId().equals(channelID)) {
                bld.setBlackChannelId(null);
                content += "Blacklist";
            }
        }
        if (!content.equals("")) {
            alertMessage = "These alerts need to be modified as the channel has been canceled:\n" + content;
            guild.getTextChannelById(alertChannel).sendMessage(alertMessage).queue();
        }
    }


    public static boolean isBotAlone(AudioChannel botChannel, AudioChannel channelLeave) {
        if((botChannel != null && channelLeave != null) && (botChannel.getId().equals(channelLeave.getId()))
        && (channelLeave.getMembers().stream().filter(member -> !member.getUser().isBot()).count() == 0)) {
            return true;
        }
        return false;
    }

    public static void handleBotLeave(Guild guild) {
        guild.getAudioManager().closeAudioConnection();
        
        PlayerManager.get().getGuildMusicManager(guild).getTrackScheduler().clearQueue();
        PlayerManager.get().getGuildMusicManager(guild).getTrackScheduler().deleteMessage();
    }


    public static void handleGreetSound(AudioChannel channelJoin, User theGuy, Guild guild) {
        String sound = Bot.getUserData(theGuy.getId()).getGreet(guild.getId());

        if(sound == null)
            return;

        PlayerManager pm = PlayerManager.get();

        String path = "rsc" + File.separator + "SoundBoard"+ File.separator + sound;


        pm.loadItemOrdered(guild, path, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                if (!guild.getAudioManager().isConnected()) guild.getAudioManager().openAudioConnection(channelJoin);

                track.setUserData(new TrackData(AudioType.GREET));
                pm.getGuildMusicManager(guild).getTrackScheduler().play(track, AudioType.GREET);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {}
            
            @Override
            public void noMatches() {}

            @Override
            public void loadFailed(FriendlyException throwable) {
                System.out.println("error: " + throwable.getMessage());
            }
        });
    }


    public static void handleCustomCommand(String commandName, SlashCommandInteractionEvent event) {
        GuildData guild = Bot.getGuildSettings().getGuild(event.getGuild().getId());
        CustomCommand command = guild.getCustomCommand(commandName);

        if(command == null) {
            return;
        }
        
        event.deferReply().queue();
        for(Task task : command.getTasks()) {
            task.execute(command, event);
        }
    }

    public static void updateCommandStatitics(SlashCommandInteractionEvent event) {
        GuildData guild = Bot.getGuildSettings().getGuild(event.getGuild().getId());
        if(!guild.getCommandStatsRoom(event.getChannel().getIdLong()))
            return;
        
        String commandName = event.getName();
        String args = event.getOptions().toString();
        DatabaseHandler.insertCommand(event.getGuild().getId(), event.getMember().getId(), commandName, args);
    }

    public static void updateCommandStatitics(CommandEvent event, Command command) {
        GuildData guild = Bot.getGuildSettings().getGuild(event.getGuild().getId());
        if(!guild.getCommandStatsRoom(event.getChannel().getIdLong()))
            return;
        
        String commandName = command.getName();
        String args = event.getArgs();
        DatabaseHandler.insertCommand(event.getGuild().getId(), event.getMember().getId(), commandName, args);
    }


}
