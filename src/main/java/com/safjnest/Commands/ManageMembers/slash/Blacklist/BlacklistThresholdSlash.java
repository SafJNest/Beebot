package com.safjnest.commands.ManageMembers.slash.Blacklist;

import java.util.Arrays;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.model.guild.GuildDataHandler;
import com.safjnest.util.CommandsLoader;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class BlacklistThresholdSlash extends SlashCommand{

    private GuildDataHandler gs;

    public BlacklistThresholdSlash(String father, GuildDataHandler gs){
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();
        this.help = new CommandsLoader().getString(name, "help", father.toLowerCase());
        this.cooldown = new CommandsLoader().getCooldown(this.name, father.toLowerCase());
        this.category = new Category(new CommandsLoader().getString(father.toLowerCase(), "category"));
        this.userPermissions = new Permission[]{Permission.BAN_MEMBERS};
        this.botPermissions = new Permission[]{Permission.BAN_MEMBERS};
        this.options = Arrays.asList(
            new OptionData(OptionType.INTEGER, "threshold", "Ban threshold", true)
                .setMinValue(3)    
                .setMaxValue(100));
        
        this.gs = gs;
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        String threshold = event.getOption("threshold").getAsString();

        if(!gs.getGuild(event.getGuild().getId()).setThreshold(Integer.parseInt(threshold))) {
            event.deferReply(true).addContent("Something went wrong.").queue();
            return;
        }
        
        event.deferReply(false).addContent("Blacklist threshold set to " + threshold + ".\n").queue();
        
    }
}