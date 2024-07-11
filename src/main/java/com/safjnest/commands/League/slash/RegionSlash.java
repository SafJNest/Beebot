package com.safjnest.commands.League.slash;

import java.util.Arrays;
    
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.core.Bot;
import com.safjnest.model.guild.GuildData;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;
import com.safjnest.util.LOL.RiotHandler;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.api.regions.RegionShard;

public class RegionSlash extends SlashCommand {
 
    public RegionSlash(){
        this.name = this.getClass().getSimpleName().replace("Slash", "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();

        this.options = Arrays.asList(
            RiotHandler.getLeagueShardOptions(true));
        
        commandData.setThings(this);
    }

	@Override
	protected void execute(SlashCommandEvent event) {
        event.deferReply(false).queue();
        
        GuildData gs = Bot.getGuildData(event.getGuild().getId());
        LeagueShard shard = LeagueShard.values()[Integer.parseInt(event.getOption("region").getAsString())];
        RegionShard region = shard.toRegionShard();

        if(!gs.setLeagueShard(shard)) {
            event.getHook().sendMessage("Something went wrong.").queue();
            return;
        }


        event.getHook().sendMessage("Region set to " + shard.commonName() + " | " + region.name()).queue();
	}

}
