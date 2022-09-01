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
import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.impl.R4J;
import no.stelar7.api.r4j.pojo.lol.championmastery.ChampionMastery;
import no.stelar7.api.r4j.pojo.lol.league.LeagueEntry;
import no.stelar7.api.r4j.pojo.lol.spectator.SpectatorParticipant;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @since 1.3
 */
public class Summoner extends Command {
    
    private R4J r;
    /**
     * Constructor
     */
    public Summoner(R4J r){
        this.name = this.getClass().getSimpleName();
        this.aliases = new JSONReader().getArray(this.name, "alias");
        this.help = new JSONReader().getString(this.name, "help");
        this.cooldown = new JSONReader().getCooldown(this.name);
        this.category = new Category(new JSONReader().getString(this.name, "category"));
        this.arguments = new JSONReader().getString(this.name, "arguments");
        this.r = r;
    }

    /**
     * This method is called every time a member executes the command.
     */
	@Override
	protected void execute(CommandEvent event) {
        String args = event.getArgs();
        no.stelar7.api.r4j.pojo.lol.summoner.Summoner s = r.getLoLAPI().getSummonerAPI().getSummonerByName(LeagueShard.EUW1, args);
        try {
            LeagueEntry entry = r.getLoLAPI().getLeagueAPI().getLeagueEntries(LeagueShard.EUW1, s.getSummonerId()).get(0);
            EmbedBuilder builder = new EmbedBuilder();
            builder.setAuthor(event.getAuthor().getName());
            builder.setColor(new Color(250,225,56));
            builder.setThumbnail("https://ddragon.leagueoflegends.com/cdn/12.16.1/img/profileicon/"+s.getProfileIconId()+".png");
            builder.addField("Level:", String.valueOf(s.getSummonerLevel()), true);
            builder.addField("Rank", 
                            entry.getTier() + " " + entry.getRank()+ " " +String.valueOf(entry.getLeaguePoints()) + " LP\n"
                            + entry.getWins() + "W/"+entry.getLosses()+"L\n"
                            + "Winrate:" + Math.ceil((Double.valueOf(entry.getWins())/Double.valueOf(entry.getWins()+entry.getLosses()))*100)+"%", true);
            String masteryString = "";
            int cont = 0;
            for(ChampionMastery mastery : s.getChampionMasteries()){
                masteryString += "[" + mastery.getChampionLevel() + "] " + r.getDDragonAPI().getChampion(mastery.getChampionId()).getName() + " " + mastery.getChampionPoints() + " points\n";
                if(cont == 2)
                    break;
                cont++;
            }
            builder.addField("Top 3 Champ", masteryString, false);
            String activity = "";
            try {
                for(SpectatorParticipant partecipant : s.getCurrentGame().getParticipants()){
                    if(partecipant.getSummonerId().equals(s.getSummonerId())){
                        activity = "Playing a " + s.getCurrentGame().getGameMode().name()+ " as " + r.getDDragonAPI().getChampion(partecipant.getChampionId()).getName();
                        break;
                    }
                }
            } catch (Exception e) {
                activity = "Not in a game";
            }
            builder.addField("Activity", activity, true);
            event.reply(builder.build());
            
        } catch (Exception e) {
            e.printStackTrace();
        }

	}

}
