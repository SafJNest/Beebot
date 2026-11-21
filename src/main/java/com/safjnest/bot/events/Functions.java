package com.safjnest.bot.events;

import java.util.HashMap;
import java.util.List;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandClient;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.bot.audio.GuildMusicManager;
import com.safjnest.bot.audio.PlayerManager;
import com.safjnest.bot.audio.TrackData;
import com.safjnest.bot.audio.types.AudioType;
import com.safjnest.bot.Bot;
import com.safjnest.bot.cache.managers.GuildCache;
import com.safjnest.bot.cache.managers.SoundCache;
import com.safjnest.bot.model.AliasData;
import com.safjnest.bot.model.UserData;
import com.safjnest.bot.model.guild.BlacklistData;
import com.safjnest.bot.model.guild.GuildData;
import com.safjnest.bot.model.guild.MemberData;
import com.safjnest.bot.model.guild.alert.AlertData;
import com.safjnest.bot.model.guild.alert.AlertType;
import com.safjnest.bot.model.guild.alert.RewardData;
import com.safjnest.database.sql.QueryRecord;
import com.safjnest.database.sql.database.BotDB;
import com.safjnest.sound.model.Sound;
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
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;

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
        TextChannel channel = null;
        try{
            channel = e.getChannel().asTextChannel();
        }
        catch(IllegalStateException err) {
            return;
        }

        Guild guild = e.getGuild();
        User newGuy = e.getAuthor();

        MemberData.Experience result = GuildCache.receiveExperience(guild.getId(), newGuy.getId(), channel.getId());
        if (!result.saved() || !result.leveledUp())
            return;

        int lvl = result.level();

        RewardData reward = guildData.getAlert(AlertType.REWARD, lvl);
        if (reward != null && reward.isValid()) {
            String message = reward.getMessage();
            String privateMessage = reward.hasPrivateMessage() ? reward.getPrivateMessage() : message;

            String[] roles = reward.getRolesAsArray();
            message = message.replace("#user", newGuy.getAsMention());
            message = message.replace("#level", String.valueOf(lvl));

            privateMessage = privateMessage.replace("#user", newGuy.getAsMention());
            privateMessage = privateMessage.replace("#level", String.valueOf(lvl));

            String mentionedRoles = "";
            for (String roleID : roles) {
                Role role = guild.getRoleById(roleID);
                if (role == null) continue;
                mentionedRoles += role.getAsMention() + ", ";        
            }

            mentionedRoles = mentionedRoles.substring(0, mentionedRoles.length() - 2);

            message = message.replace("#role", mentionedRoles);
            privateMessage = privateMessage.replace("#role", mentionedRoles);
            
            givesRoles(newGuy, guild, roles);

            final String finalMessage = message;
            final String finalPrivateMessage = privateMessage;

            switch (reward.getSendType()) {
                case CHANNEL:
                    channel.sendMessage(finalMessage).queue();
                    break;
                case PRIVATE:
                    newGuy.openPrivateChannel().queue(channelPrivate -> {
                        channelPrivate.sendMessage(finalPrivateMessage).queue();
                    });
                    break;
                case BOTH:
                    channel.sendMessage(finalMessage).queue();
                    newGuy.openPrivateChannel().queue(channelPrivate -> {
                        channelPrivate.sendMessage(finalPrivateMessage).queue();
                    });
                    break;
            }
            


            RewardData toDelete = null;
            if ((toDelete = guildData.getLowerReward(lvl)) != null && toDelete.isTemporary()) {
                roles = toDelete.getRolesAsArray();
                for (String roleID : roles) {
                    Role role = guild.getRoleById(roleID);
                    if (role == null)
                        continue;

                    try { guild.removeRoleFromMember(UserSnowflake.fromId(newGuy.getId()), role).queue(); } catch (Exception erole) { }
                    
                }
            }
            return;
        }
            
        AlertData alert = guildData.getAlert(AlertType.LEVEL_UP);
        if (alert != null && alert.isValid()) {
            String message = alert.getMessage();
            String privateMessage = alert.hasPrivateMessage() ? alert.getPrivateMessage() : message;

            message = message.replace("#user", newGuy.getAsMention());
            message = message.replace("#level", String.valueOf(lvl));

            privateMessage = privateMessage.replace("#user", newGuy.getAsMention());
            privateMessage = privateMessage.replace("#level", String.valueOf(lvl));

            final String finalMessage = message;
            final String finalPrivateMessage = privateMessage;
            switch (alert.getSendType()) {
                case CHANNEL:
                    channel.sendMessage(finalMessage).queue();
                    break;
                case PRIVATE:
                    newGuy.openPrivateChannel().queue(channelPrivate -> {
                        channelPrivate.sendMessage(finalPrivateMessage).queue();
                    });
                    break;
                case BOTH:
                    channel.sendMessage(finalMessage).queue();
                    newGuy.openPrivateChannel().queue(channelPrivate -> {
                        channelPrivate.sendMessage(finalPrivateMessage).queue();
                    });
                    break;
            }

            return;
        }
    }

    public static void handleAlert(User theGuy, Guild guild, AlertType type) {
        MessageChannel channel = null;

        AlertData alert = GuildCache.getGuildOrPut(guild.getId()).getAlert(type);
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
        String privateMessage = alert.hasPrivateMessage() ? alert.getPrivateMessage().replace("#user", theGuy.getAsMention()) : message;

        if (alert.getRoles() != null && !alert.getRoles().isEmpty()) {
            String[] roles = alert.getRoles().values().toArray(new String[0]);
            String mentionedRoles = "";
            for (String role : roles) {
                mentionedRoles += guild.getRoleById(role).getAsMention() + ", ";
            }
            mentionedRoles = mentionedRoles.substring(0, mentionedRoles.length() - 2);
            message = message.replace("#role", mentionedRoles);
            privateMessage = privateMessage.replace("#role", mentionedRoles);

            givesRoles(theGuy, guild, roles);
        }

        final String finalMessage = privateMessage;

        switch (alert.getSendType()) {
            case CHANNEL:
                channel.sendMessage(message).queue();
                break;
            case PRIVATE:
                if (theGuy.isBot()) break;
                theGuy.openPrivateChannel().queue(channelPrivate -> {
                    channelPrivate.sendMessage(finalMessage).queue();
                });
                break;
            case BOTH:
                channel.sendMessage(message).queue();
                
                if (theGuy.isBot()) break;
                theGuy.openPrivateChannel().queue(channelPrivate -> {
                    channelPrivate.sendMessage(finalMessage).queue();
                });
                break;
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

            try { guild.addRoleToMember(theGuy, r).queue();} 
            catch (Exception e) { }
            
        }
    }

    public static void handleBlacklist(User badGuy, Guild guild) {
        MessageChannel channel = null;
        GuildData guildData = GuildCache.getGuildOrPut(guild.getId());


        int threshold = guildData.getThreshold();
        if(threshold == 0)
            return;
        
        int times = BotDB.getBlacklistBan(badGuy.getId());

        if(!guildData.blacklistEnabled() || guildData.getThreshold() == 0 || guildData.getThreshold() > times)
            return;
        
        EmbedBuilder eb = new EmbedBuilder();
        eb.setAuthor(guild.getJDA().getSelfUser().getName());
        eb.setThumbnail(badGuy.getAvatarUrl());
        eb.setColor(Bot.getColor());
        eb.setTitle(":radioactive:Blacklist:radioactive:");
        eb.setDescription("The new member " + badGuy.getAsMention() + " is on the blacklist for being banned in " + times + " different guilds.\nYou have the discretion to choose the next steps.");

        channel = guild.getTextChannelById(guildData.getBlackChannelId());

        Button kick = Button.primary("kick-" + badGuy.getId(), "Kick");
        Button ban = Button.primary("ban-" + badGuy.getId(), "Ban");
        Button ignore = Button.primary("ignore-" + badGuy.getId(), "Ignore");


        kick = kick.withStyle(ButtonStyle.PRIMARY);
        ban = ban.withStyle(ButtonStyle.PRIMARY);
        ignore = ignore.withStyle(ButtonStyle.SUCCESS);
        channel.sendMessageEmbeds(eb.build()).setComponents(ActionRow.of(ignore, kick, ban)).queue();
    }

    public static void handleBlacklistAlert(User badGuy, Guild guild) {
        GuildData guildData = GuildCache.getGuildOrPut(guild.getId());
        int threshold = guildData.getThreshold();

        if(threshold == 0)
            return;
        
        BotDB.insertUserBlacklist(badGuy.getId(), guild.getId());

        int times = 0;
        times = times + BotDB.getBannedTimes(badGuy.getId());

        List<QueryRecord> guilds = BotDB.getGuildByThreshold(times, guild.getId());
        if(guilds == null)
            return;
        
        EmbedBuilder eb = new EmbedBuilder();
        eb.setAuthor(guild.getJDA().getSelfUser().getName());
        eb.setColor(Bot.getColor());
        eb.setThumbnail(badGuy.getAvatarUrl());
        eb.setTitle(":radioactive:Blacklist:radioactive:");
        eb.setDescription("The member " + badGuy.getAsMention() + " is on the blacklist for being banned in " + times + " different guilds.\nYou have the discretion to choose the next steps.");
        for(QueryRecord g : guilds){
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
            channel.sendMessageEmbeds(eb.build()).setComponents(ActionRow.of(ignore, kick, ban)).queue();
        }
    }

    public static void handleChannelDeleteAlert(Guild guild, String channelID) {
        String alertChannel = guild.getDefaultChannel().getId();
        String content = GuildCache.withGuild(guild.getId(), cached -> {
            String changed = "";

            for (AlertData alert : cached.getAlerts().values()) {
                if (channelID.equals(alert.getChannelId()) && alert.setAlertChannel(null))
                    changed += alert.getType().getDescription() + ", ";
            }

            BlacklistData blacklist = cached.getBlacklistData();

            if (channelID.equals(blacklist.getBlackChannelId()) && blacklist.setBlackChannelId(null))
                changed += "Blacklist";

            return changed;
        });

        if (!content.isEmpty()) {
            String alertMessage = "These alerts need to be modified as the channel has been canceled:\n" + content;
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

        GuildMusicManager music = PlayerManager.get().getGuildMusicManager(guild);
        music.resetPlayback();
        music.getTrackScheduler().deleteMessage();
    }


    public static void handleGreetSound(AudioChannel channelJoin, User theGuy, Guild guild) {
        Sound sound = SoundCache.greetForPlayback(theGuy.getId(), guild.getId());

        if(sound == null)  return;

        PlayerManager pm = PlayerManager.get();

        pm.loadSound(guild, sound, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                if (!guild.getAudioManager().isConnected()) guild.getAudioManager().openAudioConnection(channelJoin);

                AudioChannel playbackChannel = guild.getAudioManager().getConnectedChannel();
                if (playbackChannel == null)
                    playbackChannel = channelJoin;

                String channelId = playbackChannel == null ? null : playbackChannel.getId();
                SoundCache.withSound(sound.getId(), current -> current.increaseUserPlays(theGuy.getId(), guild.getId(), channelId, AudioType.GREET));
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

    public static void updateCommandStatitics(SlashCommandInteractionEvent event) {
        String guildId = event.isFromGuild() ? event.getGuild().getId() : "0";

        GuildData guild = GuildCache.getGuildOrPut(guildId);
        if(!guild.getCommandStatsRoom(event.getChannel().getId()))
            return;
                
        String commandName = event.getName();
        String args = event.getOptions().toString();
        BotDB.insertCommand(guildId, event.getUser().getId(), commandName, args);
    }

    public static void updateCommandStatitics(CommandEvent event, Command command) {
        GuildData guild = GuildCache.getGuildOrPut(event.getGuild().getId());
        if(!guild.getCommandStatsRoom(event.getChannel().getId()))
            return;
        
        String commandName = command.getName();
        String args = event.getArgs();
        BotDB.insertCommand(event.getGuild().getId(), event.getMember().getId(), commandName, args);
    }

    public static void handleRoleDeleteAlert(Guild guild, String role_id) {
        GuildCache.withGuild(guild.getId(), cached -> {
            for (AlertData alert : cached.getAlerts().values())
                alert.removeRole(role_id);

            return null;
        });
    }


}
