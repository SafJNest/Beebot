package com.safjnest.Commands.LOL;

import java.awt.Color;
import java.io.File;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.Utilities.JSONReader;


import net.dv8tion.jda.api.EmbedBuilder;
import no.stelar7.api.r4j.basic.APICredentials;
import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.impl.R4J;
import no.stelar7.api.r4j.impl.R4J.LOLAPI;
import no.stelar7.api.r4j.impl.lol.builders.champion.ChampionBuilder;
import no.stelar7.api.r4j.impl.lol.builders.league.LeagueBuilder;
import no.stelar7.api.r4j.impl.lol.lcu.LCUApi;
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
public class FreeChamp extends Command {
    
    /**
     * Constructor
     */
    public FreeChamp(){
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
        ChampionBuilder builder = new ChampionBuilder().withPlatform(LeagueShard.EUW1);
        ChampionRotationInfo c = builder.getFreeToPlayRotation();
        EmbedBuilder eb = new EmbedBuilder();
        eb.setAuthor(event.getAuthor().getName());
        eb.setColor(new Color(0,255,0));
        eb.setTitle("Lista dei Campioni Gratuiti della settimana:");
        String s = "";
        for(StaticChampion ce : c.getFreeChampions())
            s+=ce.getName()+"\n";
        
        String img = "iconLol.png";
        File file = new File("rsc" + File.separator + "img" + File.separator + img);
        eb.setDescription(s);
        eb.setThumbnail("attachment://" + img);
        event.getChannel().sendMessageEmbeds(eb.build())
            .addFile(file, img)
            .queue();
	}

}
