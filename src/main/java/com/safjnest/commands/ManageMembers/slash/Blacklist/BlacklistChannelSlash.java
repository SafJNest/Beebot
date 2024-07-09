package com.safjnest.commands.ManageMembers.slash.Blacklist;

import java.util.Arrays;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.model.guild.GuildDataHandler;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class BlacklistChannelSlash extends SlashCommand {

    private GuildDataHandler gs;
    
    public BlacklistChannelSlash(String father, GuildDataHandler gs){
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(father).getChild(this.name);
        
        this.aliases = commandData.getAliases();
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        this.arguments = commandData.getArguments();

        this.userPermissions = new Permission[]{Permission.BAN_MEMBERS};
        this.botPermissions = new Permission[]{Permission.BAN_MEMBERS};
        
        this.options = Arrays.asList(
            new OptionData(OptionType.CHANNEL, "channel", "Notification channel", true)
                .setChannelTypes(ChannelType.TEXT)
        );   
        commandData.setThings(this);

        this.gs = gs;
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        if(gs.getGuild(event.getGuild().getId()).getThreshold() == 0){
            event.deferReply(true).addContent("Blacklist is disabled.").queue();
            return;
        }

        String channelID = event.getOption("channel").getAsChannel().getId();

        if(!gs.getGuild(event.getGuild().getId()).setBlackChannel(channelID)) {
            event.deferReply(true).addContent("Something went wrong.").queue();
            return;
        }
        
        event.deferReply(false).addContent("Blacklist channel set to " + event.getGuild().getTextChannelById(channelID).getAsMention() + ".").queue();
    }
}