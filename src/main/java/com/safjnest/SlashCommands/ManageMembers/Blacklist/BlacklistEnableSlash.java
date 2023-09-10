package com.safjnest.SlashCommands.ManageMembers.Blacklist;

import java.util.Arrays;


import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.Utilities.CommandsLoader;
import com.safjnest.Utilities.DatabaseHandler;
import com.safjnest.Utilities.Guild.GuildSettings;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class BlacklistEnableSlash extends SlashCommand{

    private GuildSettings gs;

    public BlacklistEnableSlash(String father, GuildSettings gs){
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();
        this.help = new CommandsLoader().getString(name, "help", father.toLowerCase());
        this.cooldown = new CommandsLoader().getCooldown(this.name, father.toLowerCase());
        this.category = new Category(new CommandsLoader().getString(father.toLowerCase(), "category"));
        this.userPermissions = new Permission[]{Permission.BAN_MEMBERS};
        this.botPermissions = new Permission[]{Permission.BAN_MEMBERS};
        this.options = Arrays.asList(
            new OptionData(OptionType.INTEGER, "threshold", "Minimum ban threshold", true)
                .setMinValue(3)    
                .setMaxValue(100),
            new OptionData(OptionType.CHANNEL, "channel", "Channel to sent ban notifications", true));
        
        this.gs = gs;
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        String threshold = event.getOption("threshold").getAsString();
        String channelId = event.getOption("channel").getAsChannel().getId();


        String query = "INSERT INTO guild_settings(guild_id, bot_id, threshold, blacklist_channel)" + "VALUES('" + event.getGuild().getId() + "','" + event.getJDA().getSelfUser().getId() + "','" + threshold +"', '" + channelId + "') ON DUPLICATE KEY UPDATE threshold = '" + threshold + "', blacklist_channel = '" + channelId + "';";
        if(!DatabaseHandler.getSql().runQuery(query)){
            event.deferReply(false).addContent("Something went wrong.").queue();
            return;            
        }
        event.deferReply(false).addContent("Blacklist enabled with a threshold ban of " + threshold + ".\nNotification for bans will be sent in " + event.getGuild().getTextChannelById(channelId).getAsMention() + ".").queue();
        gs.getServer(event.getGuild().getId()).setThreshold(Integer.parseInt(threshold));
        gs.getServer(event.getGuild().getId()).setBlackChannel(channelId);
    }
}