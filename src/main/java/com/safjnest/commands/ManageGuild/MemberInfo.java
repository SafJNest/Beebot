package com.safjnest.commands.ManageGuild;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.core.Bot;
import com.safjnest.sql.DatabaseHandler;
import com.safjnest.sql.ResultRow;
import com.safjnest.util.CommandsLoader;
import com.safjnest.util.ExperienceSystem;
import com.safjnest.util.PermissionHandler;
import com.safjnest.util.LOL.RiotHandler;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.pojo.lol.summoner.Summoner;
import no.stelar7.api.r4j.pojo.shared.RiotAccount;

/**
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 1.2.5
 */
public class MemberInfo extends Command{

    public MemberInfo() {
        this.name = this.getClass().getSimpleName().toLowerCase();
        this.aliases = new CommandsLoader().getArray(this.name, "alias");
        this.help = new CommandsLoader().getString(this.name, "help");
        this.cooldown = new CommandsLoader().getCooldown(this.name);
        this.category = new Category(new CommandsLoader().getString(this.name, "category"));
        this.arguments = new CommandsLoader().getString(this.name, "arguments");
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

        String name = mentionedMember.getUser().getName();
        String id = mentionedMember.getId();

        List<String> RoleNames = PermissionHandler.getMaxFieldableRoleNames(mentionedMember.getRoles());

        String permissionNames = PermissionHandler.getFilteredPermissionNames(mentionedMember).toString();

        HashMap<String, String> lolAccounts = Bot.getUserData(id).getRiotAccounts();
        String lolAccountsString = "";
        if(lolAccounts == null || lolAccounts.isEmpty()) {
            lolAccountsString = mentionedMember.getEffectiveName() + " has not connected a riot account.";
        }
        else {
            for(String account : lolAccounts.keySet()) {
                Summoner s = RiotHandler.getSummonerByAccountId(account, LeagueShard.values()[Integer.valueOf(lolAccounts.get(account))]);
                RiotAccount riotAccount = RiotHandler.getRiotApi().getAccountAPI().getAccountByPUUID(LeagueShard.values()[Integer.valueOf(lolAccounts.get(account))].toRegionShard(), s.getPUUID());
                lolAccountsString += riotAccount.getName() + "#" + riotAccount.getTag() + " - ";
            }
            lolAccountsString = lolAccountsString.substring(0, lolAccountsString.length() - 3);
        }

        ResultRow userExp = DatabaseHandler.getUserExp(id, event.getGuild().getId());
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
        eb.setTitle(":busts_in_silhouette: **INFORMATION ABOUT " + name + "** :busts_in_silhouette:");
        eb.setThumbnail(mentionedMember.getEffectiveAvatarUrl());
        eb.setColor(Bot.getColor());

        eb.addField("Name", "```" + name + "```", true);

        eb.addField("Nickname", "```"
            + (mentionedMember.getNickname() == null
                ? "NO NICKNAME"
                : mentionedMember.getNickname())
        + "```", true);

        eb.addField("ID", "```" + id + "```" , true);
        
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
            + DatabaseHandler.getSoundsUploadedByUserCount(id)
        + "```", true);

        eb.addField("Sounds Uploaded in this server", "```" 
            + DatabaseHandler.getSoundsUploadedByUserCount(id, event.getGuild().getId())
        + "```", true);
        
        eb.addField("Total Sound played (global)", "```"
            + DatabaseHandler.getTotalPlays(id)
        + "```", true);

        eb.addField("Member joined", 
            "<t:" + mentionedMember.getTimeJoined().toEpochSecond() + ":f>" + " | <t:" + mentionedMember.getTimeJoined().toEpochSecond() + ":R>",
        false);

        eb.addField("Account created", 
            "<t:" + mentionedMember.getTimeCreated().toEpochSecond() + ":f>"  + " | <t:" + mentionedMember.getTimeCreated().toEpochSecond() + ":R>",
        false);
        
        event.reply(eb.build());    
    }
}