package com.safjnest.commands.guild;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.core.Bot;
import com.safjnest.core.cache.managers.UserCache;
import com.safjnest.sql.BotDB;
import com.safjnest.sql.QueryRecord;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;
import com.safjnest.util.ExperienceSystem;
import com.safjnest.util.PermissionHandler;
import com.safjnest.util.lol.LeagueHandler;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.pojo.lol.summoner.Summoner;
import no.stelar7.api.r4j.pojo.shared.RiotAccount;

/**
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 1.2.5
 */
public class MemberInfo extends SlashCommand {

    public MemberInfo() {
        this.name = this.getClass().getSimpleName().toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(this.name);
        
        this.aliases = commandData.getAliases();
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        this.arguments = commandData.getArguments();

        this.options = Arrays.asList(
            new OptionData(OptionType.USER, "user", "User to get information on", false),
            new OptionData(OptionType.INTEGER, "rolecharnumber", "Max number of charachters the roles filed can be (1 to 1024)", false)
                .setMinValue(1)
                .setMaxValue(1024)
        );
        commandData.setThings(this);

    }

    private static EmbedBuilder createEmbed(Guild guild, Member mentionedMember) {
        List<String> RoleNames = PermissionHandler.getMaxFieldableRoleNames(mentionedMember.getRoles());

        String permissionNames = PermissionHandler.getFilteredPermissionNames(mentionedMember).toString();

        HashMap<String, String> lolAccounts = UserCache.getUser(mentionedMember.getId()).getRiotAccounts();
        String lolAccountsString = "";
        if(lolAccounts == null || lolAccounts.isEmpty()) {
            lolAccountsString = mentionedMember.getEffectiveName() + " has not connected a riot account.";
        }
        else {
            for(String account : lolAccounts.keySet()) {
                Summoner s = LeagueHandler.getSummonerByPuuid(account, LeagueShard.values()[Integer.valueOf(lolAccounts.get(account))]);
                RiotAccount riotAccount = LeagueHandler.getRiotAccountFromSummoner(s);
                lolAccountsString += riotAccount.getName() + "#" + riotAccount.getTag() + " - ";
            }
            lolAccountsString = lolAccountsString.substring(0, lolAccountsString.length() - 3);
        }

        QueryRecord userExp = BotDB.getUserExp(mentionedMember.getId(), guild.getId());
        int exp = 0, lvl = 0, msg = 0;
        if(userExp != null) {
            exp = userExp.getAsInt("experience");
            lvl = userExp.getAsInt("level");
            msg = userExp.getAsInt("messages");
        }
        String lvlString = ExperienceSystem.getExpToLvlUp(lvl, exp) + "/" + ExperienceSystem.getExpToReachLvl(lvl);

        List<String> activityNames = new ArrayList<String>();
        mentionedMember.getActivities().forEach(activity -> activityNames.add(activity.getName()));

        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle(":busts_in_silhouette: **INFORMATION ABOUT " + mentionedMember.getEffectiveName() + "** :busts_in_silhouette:");
        eb.setThumbnail(mentionedMember.getEffectiveAvatarUrl());
        eb.setColor(Bot.getColor());

        eb.addField("Name", "```" + mentionedMember.getEffectiveName() + "```", true);

        eb.addField("Nickname", "```"
            + (mentionedMember.getNickname() == null
                ? "NO NICKNAME"
                : mentionedMember.getNickname())
        + "```", true);

        eb.addField("ID", "```" + mentionedMember.getId() + "```" , true);
        
        eb.addField("Roles [" + mentionedMember.getRoles().size() + "] " + "(Printed " + RoleNames.size() + ")", "```"
            + (RoleNames.size() == 0
                ? "NO ROLES"
                : RoleNames.toString().substring(1, RoleNames.toString().length() - 1))
        + "```", false);

        eb.addField("Status", "```"
            + mentionedMember.getOnlineStatus()
        + "```", true);

        eb.addField("Is a bot", "```"
            + ((mentionedMember.getUser().isBot())
                ? "yes"
                : "no")
        + "```" , true);

        if(activityNames.size() > 0) {
            eb.addField("Activities", "```"
                + activityNames.toString().substring(1, activityNames.toString().length() - 1)
            + "```", false);
        }

        eb.addField("Permissions", "```"
            + (mentionedMember.hasPermission(Permission.ADMINISTRATOR)
                ? "👑 Admin"
                : permissionNames.substring(1, permissionNames.length() - 1)) + " "
        + "```", false);
        
        eb.addField("League Of Legends Account [" + lolAccounts.size() + "]", "```" 
            + lolAccountsString 
        + "```", false);
        
        eb.addField("Level", "```" 
            + lvl + " (" + lvlString + ")"
        + "```", true);

        eb.addField("Experience gained", "```"
            + exp + " exp"
        + "```", true);
        
        eb.addField("Total messages sent","```" 
            + msg 
        +"```", true);
        
        eb.addField("Total Sounds Uploaded", "```" 
            + BotDB.getSoundsUploadedByUserCount(mentionedMember.getId())
        + "```", true);

        eb.addField("Sounds Uploaded in this server", "```" 
            + BotDB.getSoundsUploadedByUserCount(mentionedMember.getId(), guild.getId())
        + "```", true);
        
        eb.addField("Total Sound played (global)", "```"
            + BotDB.getTotalPlays(mentionedMember.getId())
        + "```", true);

        eb.addField("Member joined", 
            "<t:" + mentionedMember.getTimeJoined().toEpochSecond() + ":f>" + " | <t:" + mentionedMember.getTimeJoined().toEpochSecond() + ":R>",
        false);

        eb.addField("Account created", 
            "<t:" + mentionedMember.getTimeCreated().toEpochSecond() + ":f>"  + " | <t:" + mentionedMember.getTimeCreated().toEpochSecond() + ":R>",
        false);

        return eb;
    }

    @Override
    protected void execute(CommandEvent event) {
        Member mentionedMember;
        if(event.getArgs().equals("")) {
            mentionedMember = event.getMember();
        }
        else {
            mentionedMember = PermissionHandler.getMentionedMember(event, event.getArgs());
        }

        if(mentionedMember == null) {
            event.reply("Couldn't find the specified member. Please mention or write the id of a member.");
            return;
        }




        

        
        event.reply(createEmbed(event.getGuild(), mentionedMember).build());    
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        User user = event.getOption("user") == null ? event.getUser() : event.getOption("user").getAsUser();

        if(!event.getGuild().isMember(user)){
            event.reply("The specified user is not in this guild.");
            return;
        }

        Member mentionedMember = event.getGuild().getMember(user);
        

        event.deferReply(false).addEmbeds(createEmbed(event.getGuild(), mentionedMember).build()).queue();
    }
}