package com.safjnest.commands.owner;



import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.core.Bot;
import com.safjnest.core.cache.managers.GenericCache;
import com.safjnest.core.cache.managers.GuildCache;
import com.safjnest.core.cache.managers.SoundCache;
import com.safjnest.core.cache.managers.UserCache;
import com.safjnest.model.UserData;
import com.safjnest.model.customemoji.CustomEmojiHandler;
import com.safjnest.model.guild.GuildData;
import com.safjnest.model.sound.Sound;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;
import com.safjnest.util.lol.LeagueHandler;
import com.safjnest.util.twitch.TwitchClient;

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
    
    private GuildCache gs;
    private CommandEvent event;
    public PrintCache() {
        this.name = this.getClass().getSimpleName().toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(this.name);
        
        this.aliases = commandData.getAliases();
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        this.arguments = commandData.getArguments();
        this.ownerCommand = true;
        this.hidden = true;

        commandData.setThings(this);

        this.gs = GuildCache.getInstance();
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
            case "sound":
                printSounds();
                break;
            case "twitch":
                printTwitch();
                break;
            case "guildsize":
                //Bot.getGuildSettings().getGuilds().setMaxSize(Integer.valueOf(args[1]));
                event.reply("Guild size set to " + args[1]);
                break;
            case "usersize":
                //Bot.getUsers().setMaxSize(Integer.valueOf(args[1]));
                event.reply("User size set to " + args[1]);
                break;
            case "soundsize":
                //SoundHandler.getSoundCache().setMaxSize(Integer.valueOf(args[1]));
                event.reply("Sound size set to " + args[1]);
                break;
            case "clear":
                // Bot.getGuildSettings().clear();
                // Bot.getUsers().clear();
                //SoundHandler.getSoundCache().clear();
                event.reply("Cache cleared");
                break;
            default:
                break;
        }
                
    }

    private void printTwitch() {
        String msg = "";
        ArrayList<String> cache = new ArrayList<>();
        GenericCache<String, com.github.twitch4j.helix.domain.User> streamers = TwitchClient.getStreamersCache();
        for(String s : streamers.keySet()) {
            System.out.println("keyset: " + s);
            long time = streamers.expiresAfter(s);

            msg += "**" + s + "** expires " + "<t:" + ((time + System.currentTimeMillis())/1000) + ":R>" + "```"
                + "name: " + streamers.get(s).getDisplayName() + "```";
            cache.add(msg);
            msg = "";
        }

        String header = "**Tier god information about the insane beebots cache**```" + "Total Streamer: " + streamers.getSize() + " / " + streamers.getMaxSize() + "\n"
            + "Other bot information\n"
            + "Total Emojis: " + CustomEmojiHandler.getEmojis().size() + "\n"
            + "League Version: " + LeagueHandler.getVersion() + "```";
        cache.add(0, header);

        MessageChannel channel = event.getChannel();
        for(String s : cache){
            channel.sendMessage(s).queue();
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

        for(GuildData gd : gs.values()){
            try {
                if(!forbidden.contains(String.valueOf(gd.getId()))) {
                    long time = gs.expiresAfter(gd.getID());
                    totalUsers += gd.getMembers().size();
                    totalChannels += gd.getChannels().size();
                    totalAlerts += gd.isAlertsCached() ? 1 : 0;
                    totalBlackList += gd.isBlackListCached() ? 1 : 0;
                    msg += "**" + event.getJDA().getGuildById(gd.getId()).getName() + "** expires " + "<t:" + ((time + System.currentTimeMillis())/1000) + ":R>" + "```"
                        + "Prefix: " + gd.getPrefix() + "\n"
                        + "ExpSystem: " + (gd.isExperienceEnabled() ? "enabled" : "disabled") + "\n"
                        + "Members: " + gd.getMembers().size() + "\n"
                        + "Channels: " + gd.getChannels().size() + "\n"
                        + "Alerts: " + (gd.isAlertsCached() ? "cached" : "not cached") + "\n"
                        + "BlackList: " + (gd.isBlackListCached() ? "cached" : "not cached") + "```";
                    cache.add(msg);
                    msg = "";
                }

            } catch (Exception e) {
                e.printStackTrace();
               continue;
            }
        }
        SoundCache ss = SoundCache.getInstance();
        String header = "**Tier god information about the insane beebots cache**```" + "Total Guilds: " + gs.getSize() + " / " + gs.getMaxSize() + "\n"
            + "Total Users: " + UserCache.getInstance().getSize() + " / " + UserCache.getInstance().getMaxSize() + "\n"
            + "Total Sounds: " + ss.getSize() + " / " + ss.getMaxSize() + "\n"
            + "Total Members: " + totalUsers + "\n"
            + "Total Channels: " + totalChannels + "\n"
            + "Total Alerts: " + totalAlerts + "\n"
            + "Total BlackList: " + totalBlackList + "\n\n"
            + "Other bot information\n"
            + "Total Emojis: " + CustomEmojiHandler.getEmojis().size() + "\n"
            + "League Version: " + LeagueHandler.getVersion() + "```";
        cache.add(0, header);

        MessageChannel channel = event.getChannel();
        for(String s : cache){
            channel.sendMessage(s).queue();
        }
    }


    private void printUsers() {
        ArrayList<String> cache = new ArrayList<>();

        String msg = "";
        for(UserData ud : UserCache.getInstance().values()) {
            long time = UserCache.getInstance().expiresAfter(ud.getId());

            HashMap<String, String> lolAccounts = ud.getRiotAccounts();
            String lolAccountsString = "";
            if(lolAccounts == null || lolAccounts.isEmpty()) {
                lolAccountsString = "Zero accounts\n";
            }
            else {
                for(String account : lolAccounts.keySet()) {
                    Summoner s = LeagueHandler.getSummonerByAccountId(account, LeagueShard.values()[Integer.valueOf(lolAccounts.get(account))]);
                    RiotAccount riotAccount = LeagueHandler.getRiotAccountFromSummoner(s);
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



        String header = "**Tier god information about the insane beebots cache**```" + "Total Guilds: " + gs.getGuilds().size() + " / " + gs.getMaxSize() + "\n"
            + "Total Users: " + UserCache.getInstance().getSize() + " / " + UserCache.getInstance().getMaxSize() + "\n"
            + "Other bot information\n"
            + "Total Emojis: " + CustomEmojiHandler.getEmojis().size() + "\n"
            + "League Version: " + LeagueHandler.getVersion() + "```";
        cache.add(0, header);

        MessageChannel channel = event.getChannel();
        for(String s : cache){
            channel.sendMessage(s).queue();
        }

    }


    private void printSounds() {
        ArrayList<String> cache = new ArrayList<>();
        SoundCache ss = SoundCache.getInstance();

        String msg = "";
        for(Sound s : ss.values()) {
            long time = ss.expiresAfter(s.getId());

            msg += "**" + s.getName() + "** expires " + "<t:" + ((time + System.currentTimeMillis())/1000) + ":R>" + "```"
                + "Name: " + s.getName() + "(" + s.getId() + ")" + "\n"
                + "Guild: " + Bot.getJDA().getGuildById(s.getGuildId()).getName() + "\n"
                + "User: " + Bot.getJDA().retrieveUserById(s.getUserId()).complete().getName() + "\n"
                + "Tags: " + s.getFormattedTags() + "\n"
                + "Plays: " + s.getGlobalPlays() + "\n"
                + "Retrived: " + s.retriveGlobalPlays() + "\n"
                + "Likes: " + s.getLikes() + "\n"
                + "Dislikes: " + s.getDislikes()
                + "```";
            cache.add(msg);
            msg = "";
        }

        String header = "**Tier god information about the insane beebots cache**```" + "Total Guilds: " + gs.getGuilds().size() + " / " + gs.getMaxSize() + "\n"
        + "Total Users: " + UserCache.getInstance().getSize() + " / " + UserCache.getInstance().getMaxSize() + "\n"
        + "Total Sounds: " + ss.getSize() + " / " + ss.getMaxSize() + "\n"
        + "Other bot information\n"
        + "Total Emojis: " + CustomEmojiHandler.getEmojis().size() + "\n"
        + "League Version: " + LeagueHandler.getVersion() + "```";

        cache.add(0, header);

        MessageChannel channel = event.getChannel();
        for(String s : cache){
            channel.sendMessage(s).queue();
        }


    }
}
