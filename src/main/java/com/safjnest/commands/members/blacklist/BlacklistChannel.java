package com.safjnest.commands.members.blacklist;

import java.util.Arrays;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.core.cache.managers.GuilddataCache;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class BlacklistChannel extends SlashCommand {
    
    public BlacklistChannel(String father){
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(father).getChild(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();

        this.userPermissions = new Permission[]{Permission.BAN_MEMBERS};
        this.botPermissions = new Permission[]{Permission.BAN_MEMBERS};
        
        this.options = Arrays.asList(
            new OptionData(OptionType.CHANNEL, "channel", "Notification channel", true)
                .setChannelTypes(ChannelType.TEXT)
        );   
        commandData.setThings(this);
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        if(GuilddataCache.getGuild(event.getGuild()).getThreshold() == 0){
            event.deferReply(true).addContent("Blacklist is disabled.").queue();
            return;
        }

        String channelID = event.getOption("channel").getAsChannel().getId();

        if(!GuilddataCache.getGuild(event.getGuild()).setBlackChannel(channelID)) {
            event.deferReply(true).addContent("Something went wrong.").queue();
            return;
        }
        
        event.deferReply(false).addContent("Blacklist channel set to " + event.getGuild().getTextChannelById(channelID).getAsMention() + ".").queue();
    }
}