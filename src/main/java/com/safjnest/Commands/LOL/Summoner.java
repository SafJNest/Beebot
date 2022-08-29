package com.safjnest.Commands.LOL;

import java.awt.Color;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.Utilities.JSONReader;
/* 
import net.rithms.riot.constant.Region;
import net.rithms.riot.dto.Summoner.Summoner;
import net.rithms.riot.api.RiotApi;
import net.rithms.riot.api.RiotApiException;
*/

import net.dv8tion.jda.api.EmbedBuilder;
import no.stelar7.api.r4j.basic.APICredentials;
import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.api.regions.RegionShard;
import no.stelar7.api.r4j.impl.R4J;
import no.stelar7.api.r4j.impl.R4J.LOLAPI;
import no.stelar7.api.r4j.impl.lol.builders.champion.ChampionBuilder;
import no.stelar7.api.r4j.impl.lol.builders.league.LeagueBuilder;
import no.stelar7.api.r4j.impl.lol.raw.ChampionAPI;
import no.stelar7.api.r4j.impl.lol.raw.LeagueAPI;
import no.stelar7.api.r4j.impl.shared.AccountAPI;
import no.stelar7.api.r4j.pojo.lol.champion.ChampionRotationInfo;
import no.stelar7.api.r4j.pojo.lol.league.LeagueEntry;
import no.stelar7.api.r4j.pojo.lol.league.LeagueEntryList;
import no.stelar7.api.r4j.pojo.lol.league.LeagueItem;
import no.stelar7.api.r4j.pojo.lol.match.v5.ChampionStats;
import no.stelar7.api.r4j.pojo.lol.shared.BaseSpellData;
import no.stelar7.api.r4j.pojo.lol.staticdata.champion.StaticChampion;
import no.stelar7.api.r4j.pojo.lol.staticdata.champion.StaticChampionSpell;
import no.stelar7.api.r4j.pojo.lol.staticdata.shared.Image;
import no.stelar7.api.r4j.pojo.lol.staticdata.shared.SpellVars;
import no.stelar7.api.r4j.pojo.shared.RiotAccount;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @since 1.3
 */
public class Summoner extends Command {
    
    /**
     * Constructor
     */
    public Summoner(){
        this.name = this.getClass().getSimpleName();
        this.aliases = new JSONReader().getArray(this.name, "alias");
        this.help = new JSONReader().getString(this.name, "help");
        this.cooldown = new JSONReader().getCooldown(this.name);
        this.category = new Category(new JSONReader().getString(this.name, "category"));
        this.arguments = new JSONReader().getString(this.name, "arguments");
    }

    /**
     * This method is called every time a member executes the command.
     */
	@Override
	protected void execute(CommandEvent event) {
        String args = event.getArgs();
        R4J r = new R4J(new APICredentials("RGAPI-9b796e85-3845-411f-ba86-a94f03f2a54f"));
        no.stelar7.api.r4j.pojo.lol.summoner.Summoner s = r.getLoLAPI().getSummonerAPI().getSummonerByName(LeagueShard.EUW1, args);
        System.out.println(s.getName());
        try {
            LeagueEntry entry = r.getLoLAPI().getLeagueAPI().getLeagueEntries(LeagueShard.EUW1, s.getSummonerId()).get(0);
            EmbedBuilder builder = new EmbedBuilder();
            builder.setAuthor(event.getAuthor().getName());
            builder.setColor(new Color(0,255,0));
            builder.setThumbnail("https://ddragon.leagueoflegends.com/cdn/12.16.1/img/profileicon/"+s.getProfileIconId()+".png");
            builder.addField("Level:", String.valueOf(s.getSummonerLevel()), true);
            builder.addField("Rank", 
                            entry.getTier() + " " + entry.getRank()+ " " +String.valueOf(entry.getLeaguePoints()) + " juicy lps\n"
                            + entry.getWins() + "W/"+entry.getLosses()+"L\n"
                            + "Winrate:" + Math.ceil((Double.valueOf(entry.getWins())/Double.valueOf(entry.getWins()+entry.getLosses()))*100)+"%", true);
            event.reply(builder.build());
            
        } catch (Exception e) {
            e.printStackTrace();
        }

	}

}
