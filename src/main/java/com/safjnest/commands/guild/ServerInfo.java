package com.safjnest.commands.guild;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.core.Bot;
import com.safjnest.core.cache.managers.GuildCache;
import com.safjnest.model.guild.alert.AlertData;
import com.safjnest.model.guild.alert.AlertKey;
import com.safjnest.model.guild.alert.AlertType;
import com.safjnest.sql.DatabaseHandler;
import com.safjnest.sql.QueryRecord;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;
import com.safjnest.util.PermissionHandler;

import net.dv8tion.jda.api.EmbedBuilder;

/**
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 1.1.02
 */
public class ServerInfo extends SlashCommand {

    public ServerInfo(){
        this.name = this.getClass().getSimpleName().toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(this.name);
        
        this.aliases = commandData.getAliases();
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        this.arguments = commandData.getArguments();
        
        this.options = Arrays.asList(
            new OptionData(OptionType.INTEGER, "rolecharnumber", "Max number of charachters the roles filed can be (1 to 1024)", false)
                .setMinValue(1)
                .setMaxValue(1024)
        );

        commandData.setThings(this);
    }

    private static EmbedBuilder createEmbed(Guild guild) {
        HashMap<AlertKey<?>, AlertData> alerts = GuildCache.getGuildOrPut(guild.getId()).getAlerts();
        AlertData welcome = alerts.get(new AlertKey<>(AlertType.WELCOME));
        AlertData leave = alerts.get(new AlertKey<>(AlertType.LEAVE));
        AlertData lvlup = alerts.get(new AlertKey<>(AlertType.LEVEL_UP));

        QueryRecord settings = DatabaseHandler.getGuildData(guild.getId());
        
        String welcomeMessageString = null;
        if(welcome != null) {
            String channelString = welcome.getChannelId() == null ? "No channel set" : Bot.getJDA().getTextChannelById(welcome.getChannelId()).getName();
            welcomeMessageString = welcome.getMessage()
                + " [" + channelString + "]"
                + " [" + (welcome.isEnabled() ? "on" : "off") + "]"
            + "\n\n";
        }
        
        String leaveMessageString = null;
        if(leave != null) {
            String channelString = leave.getChannelId() == null ? "No channel set" : Bot.getJDA().getTextChannelById(leave.getChannelId()).getName();
            leaveMessageString = leave.getMessage()
                + " [" + channelString + "]"
                + " [" + (leave.isEnabled() ? "on" : "off") + "]"
            + "\n\n";
        }

        String blacklistString = null;
        if(settings.get("blacklist_channel") != null) {
            blacklistString = "A total of " + DatabaseHandler.getBannedTimesInGuild(guild.getId()) + " users have been banned from this guild"
                + " [" + Bot.getJDA().getChannelById(TextChannel.class, settings.get("blacklist_channel")).getName() + "]"
                + " [" + (settings.getAsBoolean("blacklist_enabled") ? "on" : "off") + "]"
            + "\n\n";
        }

        String lvlUpString = null;
        if(lvlup != null) {
            lvlUpString = lvlup.getMessage()
                + " [" + (lvlup.isEnabled() ? "on" : "off") + "]"
            + "\n\n";
        }

        List<String> RoleNames = PermissionHandler.getMaxFieldableRoleNames(guild.getRoles());

        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle(":desktop: **SERVER INFORMATION** :desktop:");
        eb.setThumbnail(guild.getIconUrl());
        eb.setColor(Bot.getColor());

        eb.addField("Server name", "```" + guild.getName() + "```", true);

        eb.addField("Owner ID", "```" + guild.getOwnerId() + "```" , true);

        eb.addField("Server Description", "```" 
            + ((guild.getDescription() == null) 
                ? "No description" 
                : guild.getDescription()) 
        + "```", false);

        eb.addField("Server ID", "```" + guild.getId() + "```" , true);

        eb.addField("Region", "```" + guild.getLocale().toString() + "```", true);

        eb.addField("Total number of members [" + String.valueOf(guild.getMemberCount()) + "]", "```"
            + "Members: " + guild.getMembers().stream()
                .filter(member -> !member.getUser().isBot()).count()
            + " | Bots: " + guild.getMembers().stream()
                .filter(member -> member.getUser().isBot()).count()
        + "```", false);

        eb.addField("Boost tier", "```" + guild.getBoostTier().name() + "```", true);

        eb.addField("Boost number", "```" + String.valueOf(guild.getBoostCount()) + "```", true);

        eb.addField("Booster role", "```" 
            + (guild.getBoostRole() == null
                ? "NONE"
                : guild.getBoostRole().getName())
        + "```", true);
        
        eb.addField("Welcome Message", "```" 
            + ((welcomeMessageString == null)
                ? "No welcome message set for this guild, use /help setwelcomemessage for more information"
                : welcomeMessageString)
        +  "```", false);
        
        eb.addField("Leave Message", "```" 
            + ((leaveMessageString == null)
                ? "No leave message set for this guild, use /help setleavemessage for more information"
                : leaveMessageString)
        +  "```", false);
        
        eb.addField("Level Up Message", "```" 
            + ((lvlUpString == null)
                ? "No levelup message set for this guild, use /help setlevelupmessage for more information"
                : lvlUpString)
        +  "```", false);

        eb.addField("Blacklist", "```" 
            + ((blacklistString == null)
                ? "No blacklist set for this guild, use /help blacklist for more information"
                : blacklistString)
        +  "```", false);

        eb.addField("Categories and channels [" + guild.getChannels().size() + "]", "```" 
            +    "Categories: "    + guild.getCategories().size() 
            + " | Text channels: "  + guild.getTextChannels().size() 
            + " | Voice channels: " + guild.getVoiceChannels().size() 
            + " | Stage channels: " + guild.getStageChannels().size() 
            + " | Announcement channels: " + guild.getForumChannels().size()
        + "```", false);

        eb.addField("Emojis [" + (guild.getEmojis().size()+guild.getStickers().size()) + "]", "```" 
            +    "Emojis: " + guild.getEmojis().stream()
                                .filter(emote -> emote.isAvailable()).count()
            + " | Gifs: " + guild.getStickers().stream()
                                .filter(emote -> emote.isAvailable()).count()
        + "```", false);

        eb.addField("Explicit content", "```" + guild.getExplicitContentLevel().name() + "```", true);

        eb.addField("NSFW", "```" + guild.getNSFWLevel().toString() + "```", true);

        eb.addField("Required MFA", "```" + guild.getRequiredMFALevel().toString() + "```", true);

        eb.addField("Server roles [" + guild.getRoles().size() + "] (printed " + RoleNames.size() + ")" , "```" 
            + ((RoleNames.size() > 0)
                ? RoleNames.toString().substring(1, RoleNames.toString().length() - 1)
                : "NO ROLES")
        + "```", false);

        eb.addField("Server created on", "<t:" + guild.getTimeCreated().toEpochSecond() + ":f> | "
            + "<t:" + guild.getTimeCreated().toEpochSecond() + ":R>",
        false);

        return eb;
    }

    @Override
    protected void execute(CommandEvent event) {
        Guild guild;
        if(!event.getArgs().equals("") && PermissionHandler.isUntouchable(event.getAuthor().getId()))
            guild = PermissionHandler.getGuild(event, event.getArgs());
        else 
            guild = event.getGuild();
            
        if(guild == null) {
            event.reply("Couldn't find the specified guild. Please write the id of the guild and make sure the bot is in that guild.");
            return;
        }



        event.reply(createEmbed(guild).build());
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        Guild guild = event.getGuild();

        event.deferReply(false).addEmbeds(createEmbed(guild).build()).queue();
    }
}