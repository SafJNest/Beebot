package com.safjnest.commands.Settings.slash.LevelUp;

import java.util.Arrays;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.model.guild.GuildDataHandler;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;

import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class LevelUpUpdateTimeSlash extends SlashCommand{
    private GuildDataHandler gs;

    public LevelUpUpdateTimeSlash(String father, GuildDataHandler gs){
        this.gs = gs;
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(father).getChild(this.name);

        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        
        this.options = Arrays.asList(
            new OptionData(OptionType.INTEGER, "second", "Set the update interval in seconds", true)
                .setMinValue(1)
                .setMaxValue(60 * 60)
        );

        commandData.setThings(this);
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        int updateTime = event.getOption("second").getAsInt();
        //TODO: this command will be only for vip user
        String guildId = event.getGuild().getId();
        String userId = event.getUser().getId();
        if(!gs.getGuild(guildId).getMemberData(userId).setUpdateTime(updateTime)) { 
            event.deferReply(true).addContent("Something went wrong.").queue();
            return;
        }

        event.deferReply(false).addContent("Now you will gain experience every " + updateTime + " seconds").queue();
    }
}