package com.safjnest.commands.Owner;



import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.core.Bot;
import com.safjnest.core.CacheMap;
import com.safjnest.model.UserData;
import com.safjnest.model.customemoji.CustomEmojiHandler;
import com.safjnest.model.guild.GuildData;
import com.safjnest.model.guild.GuildDataHandler;
import com.safjnest.util.CommandsLoader;
import com.safjnest.util.LOL.RiotHandler;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.pojo.lol.summoner.Summoner;
import no.stelar7.api.r4j.pojo.shared.RiotAccount;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * 
 * 
 * @since 1.0
 */
public class PrintCache extends Command {
    
    private GuildDataHandler gs;
    private CommandEvent event;
    public PrintCache(GuildDataHandler gs) {
        this.name = this.getClass().getSimpleName().toLowerCase();
        this.aliases = new CommandsLoader().getArray(this.name, "alias");
        this.help = new CommandsLoader().getString(this.name, "help");
        this.cooldown = new CommandsLoader().getCooldown(this.name);
        this.category = new Category(new CommandsLoader().getString(this.name, "category"));
        this.arguments = new CommandsLoader().getString(this.name, "arguments");
        this.ownerCommand = true;
        this.hidden = true;

        this.gs = gs;
    }

    @Override
    protected void execute(CommandEvent event) {
        String args[] = event.getArgs().split(" ", 2);
        this.event = event;

        if (args[0].isEmpty()) { 
            printGuilds();
            return;
        }

        switch (args[0]) {
            case "user":
                printUsers();
                break;
            case "guildsize":
                Bot.getGuildSettings().getGuilds().setMaxSize(Integer.valueOf(args[1]));
                event.reply("Guild size set to " + args[1]);
                break;
            case "usersize":
                Bot.getUsers().setMaxSize(Integer.valueOf(args[1]));
                event.reply("User size set to " + args[1]);
                break;
            case "clear":
                Bot.getGuildSettings().getGuilds().clear();
                Bot.getUsers().clear();
                event.reply("Cache cleared");
                break;
            default:
                break;
        }
                
    }

    private void printGuilds() {
        String msg = "";
        List<String> forbidden = List.of(CustomEmojiHandler.getForbiddenServers());
        ArrayList<String> cache = new ArrayList<>();

        int totalUsers = 0;
        int totalChannels = 0;
        int totalAlerts = 0;
        int totalBlackList = 0;

        for(GuildData gd : gs.getGuilds().values(false)){
            try {
                if(!forbidden.contains(String.valueOf(gd.getId()))) {
                    long time = gs.getGuilds().getExpirationTime(gd.getID());
                    totalUsers += gd.getUsers().size();
                    totalChannels += gd.getChannels().size();
                    totalAlerts += gd.isAlertsCached() ? 1 : 0;
                    totalBlackList += gd.isBlackListCached() ? 1 : 0;
                    msg += "**" + event.getJDA().getGuildById(gd.getId()).getName() + "** expires " + "<t:" + ((time + System.currentTimeMillis())/1000) + ":R>" + "```"
                        + "Prefix: " + gd.getPrefix() + "\n"
                        + "ExpSystem: " + (gd.isExpSystemEnabled() ? "enabled" : "disabled") + "\n"
                        + "Members: " + gd.getUsers().size() + "\n"
                        + "Channels: " + gd.getChannels().size() + "\n"
                        + "Alerts: " + (gd.isAlertsCached() ? "cached" : "not cached") + "\n"
                        + "BlackList: " + (gd.isBlackListCached() ? "cached" : "not cached") + "```";
                    cache.add(msg);
                    msg = "";
                }

            } catch (Exception e) {
               continue;
            }
        }

        String header = "**Tier god information about the insane beebots cache**```" + "Total Guilds: " + gs.getGuilds().size() + " / " + gs.getGuilds().getMaxSize() + "\n"
            + "Total Users: " + Bot.getUsers().size() + " / " + Bot.getUsers().getMaxSize() + "\n"
            + "Total Members: " + totalUsers + "\n"
            + "Total Channels: " + totalChannels + "\n"
            + "Total Alerts: " + totalAlerts + "\n"
            + "Total BlackList: " + totalBlackList + "\n\n"
            + "Other bot information\n"
            + "Total Emojis: " + CustomEmojiHandler.getEmojis().size() + "\n"
            + "League Version: " + RiotHandler.getVersion() + "```";
        cache.add(0, header);

        MessageChannel channel = event.getChannel();
        for(String s : cache){
            channel.sendMessage(s).queue();
        }
    }


    private void printUsers() {
        ArrayList<String> cache = new ArrayList<>();
        CacheMap<String, UserData> users = Bot.getUsers();

        String msg = "";
        for(UserData ud : users.values(false)) {
            long time = users.getExpirationTime(ud.getId());

            HashMap<String, String> lolAccounts = ud.getRiotAccounts();
            String lolAccountsString = "";
            if(lolAccounts == null || lolAccounts.isEmpty()) {
                lolAccountsString = "Zero accounts\n";
            }
            else {
                for(String account : lolAccounts.keySet()) {
                    Summoner s = RiotHandler.getSummonerByAccountId(account, LeagueShard.values()[Integer.valueOf(lolAccounts.get(account))]);
                    RiotAccount riotAccount = RiotHandler.getRiotApi().getAccountAPI().getAccountByPUUID(LeagueShard.values()[Integer.valueOf(lolAccounts.get(account))].toRegionShard(), s.getPUUID());
                    lolAccountsString += riotAccount.getName() + "#" + riotAccount.getTag() + " - ";
                }
                lolAccountsString = lolAccountsString.substring(0, lolAccountsString.length() - 3) + "\n";
            }


            msg += "**" + ud.getName() + "** expires " + "<t:" + ((time + System.currentTimeMillis())/1000) + ":R>" + "```"
                + "GlobalGreet: " + ud.getGlobalGreet() + "\n"
                + "GuildGreet: " + ud.getGreets().size() + "\n"
                + "Aliases: " + ud.getAliases().size() + "\n"
                + "Lol: " + lolAccountsString + "```";
            cache.add(msg);
            msg = "";
        }



        String header = "**Tier god information about the insane beebots cache**```" + "Total Guilds: " + gs.getGuilds().size() + " / " + gs.getGuilds().getMaxSize() + "\n"
            + "Total Users: " + users.size() + " / " + users.getMaxSize() + "\n"
            + "Other bot information\n"
            + "Total Emojis: " + CustomEmojiHandler.getEmojis().size() + "\n"
            + "League Version: " + RiotHandler.getVersion() + "```";
        cache.add(0, header);

        MessageChannel channel = event.getChannel();
        for(String s : cache){
            channel.sendMessage(s).queue();
        }

    }
}
