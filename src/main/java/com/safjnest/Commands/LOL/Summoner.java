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
import no.stelar7.api.r4j.impl.R4J;
import no.stelar7.api.r4j.impl.R4J.LOLAPI;
import no.stelar7.api.r4j.impl.lol.builders.champion.ChampionBuilder;
import no.stelar7.api.r4j.impl.lol.builders.league.LeagueBuilder;
import no.stelar7.api.r4j.impl.lol.raw.ChampionAPI;
import no.stelar7.api.r4j.impl.lol.raw.LeagueAPI;
import no.stelar7.api.r4j.pojo.lol.champion.ChampionRotationInfo;
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
        R4J r4j = new R4J(new APICredentials("RGAPI-1e6d6e75-afac-41b4-bd5b-1e5f6c9f277d"));
        ChampionBuilder builder = new ChampionBuilder().withPlatform(LeagueShard.EUW1);
        ChampionRotationInfo c = builder.getFreeToPlayRotation();
        for(StaticChampion ce : c.getFreeChampions()){
            String spl = "q";
            for(StaticChampionSpell spell : ce.getSpells()){
                EmbedBuilder b = new EmbedBuilder();
                b.setAuthor("fakerr");
                spl = Character.toString(spell.getImage().getFull().charAt(spell.getImage().getFull().indexOf(".")-1));
                spl = spl.toLowerCase();
                System.out.println("https://raw.communitydragon.org/latest/game/assets/characters/"+ce.getName().toLowerCase()+"/hud/icons2d/"+ce.getName().toLowerCase()+"_"+spl+".png");
                b.setThumbnail("https://raw.communitydragon.org/latest/game/assets/characters/"+ce.getName().toLowerCase()+"/hud/icons2d/"+ce.getName().toLowerCase()+"_"+spl+".png");
                event.reply(b.build());
            }      
            break;
        }
        
	}

}
