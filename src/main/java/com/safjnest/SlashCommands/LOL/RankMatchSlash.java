package com.safjnest.SlashCommands.LOL;

import java.awt.Color;
import java.util.Arrays;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.App;
import com.safjnest.Utilities.CommandsHandler;
import com.safjnest.Utilities.PostgreSQL;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.TeamType;
import no.stelar7.api.r4j.impl.R4J;
import no.stelar7.api.r4j.pojo.lol.league.LeagueEntry;
import no.stelar7.api.r4j.pojo.lol.spectator.SpectatorParticipant;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @since 1.3
 */
public class RankMatchSlash extends SlashCommand {
    private R4J r;
    private PostgreSQL sql;
    /**
     * Constructor
     */
    public RankMatchSlash(R4J r, PostgreSQL sql  ){
        this.name = this.getClass().getSimpleName().replace("Slash", "").toLowerCase();
        this.aliases = new CommandsHandler().getArray(this.name, "alias");
        this.help = new CommandsHandler().getString(this.name, "help");
        this.cooldown = new CommandsHandler().getCooldown(this.name);
        this.category = new Category(new CommandsHandler().getString(this.name, "category"));
        this.arguments = new CommandsHandler().getString(this.name, "arguments");
        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "user", "Summoner name you want to get data", false));
        this.r = r;
        this.sql = sql;
    }

    /**
     * This method is called every time a member executes the command.
     */
	@Override
	protected void execute(SlashCommandEvent event) {
        no.stelar7.api.r4j.pojo.lol.summoner.Summoner s = null;
        event.deferReply(true).queue();
        if(event.getOption("user") == null){
            String query = "SELECT account_id FROM lol_user WHERE discord_id = '" + event.getMember().getId() + "';";
            try {
                s = r.getLoLAPI().getSummonerAPI().getSummonerByAccount(LeagueShard.EUW1, sql.getString(query, "account_id"));
            } catch (Exception e) {
               event.deferReply().addContent("You dont have connected your Riot account.").queue();
               return;
            }
        }else{
            try {
                s = r.getLoLAPI().getSummonerAPI().getSummonerByName(LeagueShard.EUW1, event.getOption("user").getAsString());
            } catch (Exception e) {
                event.deferReply().addContent("Didn't found the user you asked for").queue();
                return;
            }
        }
        try {
            EmbedBuilder builder = new EmbedBuilder();
            builder.setTitle("Partita di: " + s.getName());
            builder.setColor(Color.decode(App.color));
            builder.setThumbnail("https://ddragon.leagueoflegends.com/cdn/12.16.1/img/profileicon/"+s.getProfileIconId()+".png");
            String blueSide = "";
            String redSide = "";
            for(SpectatorParticipant partecipant : s.getCurrentGame().getParticipants()){
                String sum = partecipant.getSummonerName();
                String stats = "";
                try {
                    LeagueEntry entry = r.getLoLAPI().getSummonerAPI().getSummonerById(LeagueShard.EUW1, partecipant.getSummonerId()).getLeagueEntry().get(0);
                    stats = entry.getTier().toLowerCase() + " " + entry.getRank()+ " " +String.valueOf(entry.getLeaguePoints()) + " LP | "
                        + Math.ceil((Double.valueOf(entry.getWins())/Double.valueOf(entry.getWins()+entry.getLosses()))*100)+"%";

                } catch (Exception e) {stats = "unranked";}
                if(partecipant.getTeam() == TeamType.BLUE)
                    blueSide += "**" + sum + "** " + stats+ "\n";
                else
                    redSide += "**" + sum + "** " + stats+ "\n";
                
            }
            builder.addField("**BLUE SIDE**", blueSide, false);
            builder.addField("**RED SIDE**", redSide, true);
            event.getHook().editOriginalEmbeds(builder.build()).queue();
            
        } catch (Exception e) {
            event.reply(s.getName() + " is not in a match.");
        }
	}

}
