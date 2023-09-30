package com.safjnest.Commands.ManageGuild;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.Utilities.CommandsLoader;
import com.safjnest.Utilities.DatabaseHandler;
import com.safjnest.Utilities.PermissionHandler;
import com.safjnest.Utilities.Bot.BotSettingsHandler;
import com.safjnest.Utilities.EXPSystem.ExpSystem;
import com.safjnest.Utilities.LOL.RiotHandler;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;

/**
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 1.2.5
 */
public class MemberInfo extends Command{

    public MemberInfo() {
        this.name = this.getClass().getSimpleName();
        this.aliases = new CommandsLoader().getArray(this.name, "alias");
        this.help = new CommandsLoader().getString(this.name, "help");
        this.cooldown = new CommandsLoader().getCooldown(this.name);
        this.category = new Category(new CommandsLoader().getString(this.name, "category"));
        this.arguments = new CommandsLoader().getString(this.name, "arguments");
    }

    @Override
    protected void execute(CommandEvent event) {
        Member mentionedMember;
        if(event.getArgs() == null) {
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

        String query = "SELECT summoner_id FROM lol_user WHERE user_id = '" + id + "';";
        ArrayList<String> accounts = DatabaseHandler.getSql().getAllRowsSpecifiedColumn(query, "summoner_id");
        String lolAccounts = "";
        if(accounts.size() == 0){
            lolAccounts = mentionedMember.getNickname() + " has not connected a riot account.";
        }
        else{
            for(String s : accounts)
                lolAccounts += RiotHandler.getSummonerBySummonerId(s).getName() + " - ";
            lolAccounts = lolAccounts.substring(0, lolAccounts.length() - 3);
        }

        query = "select exp, level, messages from exp_table where user_id ='" + id + "' and guild_id = '" + event.getGuild().getId() + "';";
        ArrayList<String> arr = DatabaseHandler.getSql().getSpecifiedRow(query, 0);
        int exp = 0, lvl = 0, msg = 0;
        if(arr != null) {
            exp = Integer.valueOf(arr.get(0));
            lvl = Integer.valueOf(arr.get(1));
            msg = Integer.valueOf(arr.get(2));
        }
        String lvlString = String.valueOf(ExpSystem.getExpToLvlUp(lvl, exp) + "/" + (ExpSystem.getExpToReachLvlFromZero(lvl + 1) - ExpSystem.getExpToReachLvlFromZero(lvl)));

        List<String> activityNames = new ArrayList<String>();
        mentionedMember.getActivities().forEach(activity -> activityNames.add(activity.getName()));
        

        EmbedBuilder eb = new EmbedBuilder();

        eb.setTitle(":busts_in_silhouette: **INFORMATION ABOUT " + name + "** :busts_in_silhouette:");
        eb.setThumbnail(mentionedMember.getAvatarUrl());
        eb.setColor(Color.decode(BotSettingsHandler.map.get(event.getJDA().getSelfUser().getId()).color));


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
        
        eb.addField("League Of Legends Account [" + accounts.size() + "]", "```" 
                    + lolAccounts 
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
        
        eb.addField("Total Sound Uploaded", "```"
                    + DatabaseHandler.getSql().getString(
                        "select count(name) as count from sound where user_id = '" + id + "';", 
                        "count")
                    + "```", true);

        eb.addField("Sound Uploaded in this server", "```"
                    + DatabaseHandler.getSql().getString(
                        "select count(name) as count from sound where guild_id = '" + event.getGuild().getId()+"' AND user_id = '" + id+"';", 
                        "count")
                    + "```", true);
        
        eb.addField("Total Sound played (global)", "```"
                    + (DatabaseHandler.getSql().getString(
                        "select sum(times) as sum from play where user_id = '" + id + "';", 
                        "sum"))
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