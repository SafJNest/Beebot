package com.safjnest.commands.ManageMembers.slash.Blacklist;


import com.safjnest.model.guild.GuildDataHandler;
import com.safjnest.util.CommandsLoader;

import java.util.ArrayList;
import java.util.Collections;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 3.0
 */
public class BlacklistSlash extends SlashCommand{

    private GuildDataHandler gs;


    public BlacklistSlash(GuildDataHandler gs){
        this.name = this.getClass().getSimpleName().replace("Slash", "").toLowerCase();
        String father = this.getClass().getSimpleName().replace("Slash", "");

        this.cooldown = new CommandsLoader().getCooldown(this.name);
        this.category = new Category(new CommandsLoader().getString(this.name, "category"));
        this.help = "json";
        
        this.gs = gs;

        ArrayList<SlashCommand> slashCommandsList = new ArrayList<SlashCommand>();
        Collections.addAll(slashCommandsList, new BlacklistChannelSlash(father, gs), new BlacklistCreateSlash(father, gs), new BlacklistThresholdSlash(father, gs), new BlacklistToggleSlash(father, gs));
        this.children = slashCommandsList.toArray(new SlashCommand[slashCommandsList.size()]);
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        gs.doSomethingSoSunxIsNotHurtBySeeingTheFuckingThingSayItsNotUsed();
    }
}
