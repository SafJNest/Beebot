package com.safjnest.commands.members.blacklist;

import java.util.Arrays;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.core.cache.managers.GuildCache;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class BlacklistEnable extends SlashCommand{

    public BlacklistEnable(String father, GuildCache gs){
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(father).getChild(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();

        this.userPermissions = new Permission[]{Permission.BAN_MEMBERS};
        this.botPermissions = new Permission[]{Permission.BAN_MEMBERS};
        this.options = Arrays.asList(
            new OptionData(OptionType.INTEGER, "threshold", "Ban threshold", true)
                .setMinValue(3)    
                .setMaxValue(100),
            new OptionData(OptionType.CHANNEL, "channel", "Notification channel", true)
        );
        commandData.setThings(this);
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        String threshold = event.getOption("threshold").getAsString();
        String channelId = event.getOption("channel").getAsChannel().getId();

        if (!GuildCache.getGuildOrPut(event.getGuild()).setBlackListData(Integer.parseInt(threshold), channelId)) {
            event.deferReply(false).addContent("Something went wrong.").queue();
            return;  
        }
        
        event.deferReply(false).addContent("Blacklist enabled with a ban threshold of " + threshold + ".\nNotification will be sent in " + event.getGuild().getTextChannelById(channelId).getAsMention() + ".").queue();
    }
}