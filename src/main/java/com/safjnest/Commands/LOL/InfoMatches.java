package com.safjnest.Commands.LOL;


import java.util.ArrayList;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.Utilities.CommandsLoader;
import com.safjnest.Utilities.LOL.RiotHandler;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import no.stelar7.api.r4j.basic.constants.api.regions.RegionShard;
import no.stelar7.api.r4j.basic.constants.types.lol.TeamType;
import no.stelar7.api.r4j.impl.R4J;
import no.stelar7.api.r4j.pojo.lol.match.v5.LOLMatch;
import no.stelar7.api.r4j.pojo.lol.match.v5.MatchParticipant;
import no.stelar7.api.r4j.pojo.lol.match.v5.PerkSelection;
import no.stelar7.api.r4j.pojo.lol.match.v5.PerkStyle;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @since 1.3
 */
public class InfoMatches extends Command {
    /**
     * Constructor
     */
    public InfoMatches() {
        this.name = this.getClass().getSimpleName();
        this.aliases = new CommandsLoader().getArray(this.name, "alias");
        this.help = new CommandsLoader().getString(this.name, "help");
        this.cooldown = new CommandsLoader().getCooldown(this.name);
        this.category = new Category(new CommandsLoader().getString(this.name, "category"));
        this.arguments = new CommandsLoader().getString(this.name, "arguments");
    }

    /**
     * This method is called every time a member executes the command.
     */
    @Override
    protected void execute(CommandEvent event) {
        no.stelar7.api.r4j.pojo.lol.summoner.Summoner s = null;
        String args = event.getArgs();
        MatchParticipant me = null;
        LOLMatch match = null;
        R4J r4j = RiotHandler.getRiotApi();
        
        s = RiotHandler.getSummonerByName(args);
        if (s == null) {
            event.reply("Didn't find this user. ");
            return;
        }
        
        EmbedBuilder eb = new EmbedBuilder();
        eb.setAuthor(s.getName());
        
        for(int i = 0; i < 5; i++){
            try {
                
                match = r4j.getLoLAPI().getMatchAPI().getMatch(RegionShard.EUROPE, s.getLeagueGames().get().get(i));
    
                for(MatchParticipant mp : match.getParticipants()){
                    if(mp.getSummonerId().equals(s.getSummonerId())){
                        me = mp;
                    }
                }
                ArrayList<String> blue = new ArrayList<>();
                ArrayList<String> red = new ArrayList<>();
                for(MatchParticipant searchMe : match.getParticipants()){
                    if(searchMe.getSummonerId().equals(s.getSummonerId()))
                        me = searchMe;
                    String supp = RiotHandler.getFormattedEmoji(event.getJDA(), searchMe.getChampionName()) 
                                    + " " 
                                    + (searchMe.getSummonerName().equals(me.getSummonerName()) 
                                        ? "**" + me.getSummonerName() + "**" 
                                        : searchMe.getSummonerName());
    
                    if(searchMe.getTeam() == TeamType.BLUE)
                        blue.add(supp);
                    else
                        red.add(supp);
                }
    
                String kda = me.getKills() + "/" + me.getDeaths()+ "/" + me.getAssists();
                String content = RiotHandler.getFormattedEmoji(event.getJDA(), me.getChampionName()) + kda + " | **Ward:** " + me.getWardsPlaced()+"\n"
                            + "**"+ getFormattedDuration((match.getGameDuration()))  + "** | **Vision:** " + me.getVisionScore() + "\n"
                            + RiotHandler.getFormattedEmoji(event.getJDA(), String.valueOf(me.getSummoner1Id()) + "_") + getFormattedRunes(me, event.getJDA(), 0) + "\n"
                            + RiotHandler.getFormattedEmoji(event.getJDA(), String.valueOf(me.getSummoner2Id()) + "_") + getFormattedRunes(me, event.getJDA(), 1) + "\n"
                            + RiotHandler.getFormattedEmoji(event.getJDA(), String.valueOf(me.getItem0())) + " " + RiotHandler.getFormattedEmoji(event.getJDA(), String.valueOf(me.getItem1())) + " " + RiotHandler.getFormattedEmoji(event.getJDA(), String.valueOf(me.getItem2())) + " " + RiotHandler.getFormattedEmoji(event.getJDA(), String.valueOf(me.getItem3())) + " " + RiotHandler.getFormattedEmoji(event.getJDA(), String.valueOf(me.getItem4())) + " " + RiotHandler.getFormattedEmoji(event.getJDA(), String.valueOf(me.getItem5())) + " " + RiotHandler.getFormattedEmoji(event.getJDA(), String.valueOf(me.getItem6()));
                eb.addField(
                    match.getQueue().commonName() + ": " + (me.didWin() ? "WIN" : "LOSE") , content, true);
                
                String people = "";
                for(int j = 0; j < 5; j++)
                    people += blue.get(j) + " "+ red.get(j) + "\n";
    
                eb.addField("Participant", people, true);
                eb.addBlankField(true);
            } catch (Exception e) {
                continue;
            }
        }
        event.reply(eb.build());

    }

   private String getFormattedDuration(int seconds){
        int S = seconds % 60;
        int H = seconds / 60;
        int M = H % 60;
        return M + "m: " + S + "s";
    } 

    private String getFormattedRunes(MatchParticipant me, JDA jda, int row){
        String prova = "";
        PerkStyle perkS = me.getPerks().getPerkStyles().get(row);
        
        prova += RiotHandler.getFormattedEmoji(jda, RiotHandler.getFatherRune(perkS.getSelections().get(0).getPerk()));
        for(PerkSelection perk: perkS.getSelections()){
            prova += RiotHandler.getFormattedEmoji(jda, perk.getPerk());
        }
        return prova;
        
    }

}
