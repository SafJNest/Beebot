package com.safjnest.SlashCommands.LOL;

import java.util.ArrayList;
import java.util.Arrays;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.Utilities.CommandsLoader;
import com.safjnest.Utilities.LOL.RiotHandler;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import no.stelar7.api.r4j.basic.constants.api.regions.RegionShard;
import no.stelar7.api.r4j.basic.constants.types.lol.TeamType;
import no.stelar7.api.r4j.impl.R4J;
import no.stelar7.api.r4j.pojo.lol.match.v5.LOLMatch;
import no.stelar7.api.r4j.pojo.lol.match.v5.MatchParticipant;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @since 1.3
 */
public class InfoMatchesSlash extends SlashCommand {
 
    /**
     * Constructor
     */
    public InfoMatchesSlash(){
        this.name = this.getClass().getSimpleName().replace("Slash", "").toLowerCase();
        this.aliases = new CommandsLoader().getArray(this.name, "alias");
        this.help = new CommandsLoader().getString(this.name, "help");
        this.cooldown = new CommandsLoader().getCooldown(this.name);
        this.category = new Category(new CommandsLoader().getString(this.name, "category"));
        this.arguments = new CommandsLoader().getString(this.name, "arguments");
        this.options = Arrays.asList(new OptionData(OptionType.STRING, "user", "Summoner name you want to get data", true));
    }

    /**
     * This method is called every time a member executes the command.
     */
	@Override
	protected void execute(SlashCommandEvent event) {
        event.deferReply(false).queue();
        no.stelar7.api.r4j.pojo.lol.summoner.Summoner s = null;
        s = RiotHandler.getSummonerByName(event.getOption("user").getAsString());
        MatchParticipant me = null;
        LOLMatch match = null;
        R4J r4j = RiotHandler.getRiotApi();
        if(s == null){
            event.getHook().editOriginal("Didn't find this user. ").queue();
            return;
        }
        EmbedBuilder eb = new EmbedBuilder();
        eb.setAuthor(s.getName());

        for(int i = 0; i < 5; i++){
            match = r4j.getLoLAPI().getMatchAPI().getMatch(RegionShard.EUROPE, s.getLeagueGames().get().get(i));

            for(MatchParticipant mp : match.getParticipants()){
                if(mp.getSummonerId().equals(s.getSummonerId())){
                    me = mp;
                }
            }
            ArrayList<MatchParticipant> blue = new ArrayList<>();
            ArrayList<MatchParticipant> red = new ArrayList<>();
            for(MatchParticipant searchMe : match.getParticipants()){
                if(searchMe.getSummonerId().equals(s.getSummonerId()))
                    me = searchMe;
                if(searchMe.getTeam() == TeamType.BLUE)
                    blue.add(searchMe);
                else
                    red.add(searchMe);
            }

            String kda = me.getKills() + "/" + me.getDeaths()+ "/" + me.getAssists() + "\n";
            String content = RiotHandler.getFormattedEmoji(event.getJDA(), me.getChampionName()) + kda + "\n"
                        + RiotHandler.getFormattedEmoji(event.getJDA(), String.valueOf(me.getSummoner1Id()) + "_") + RiotHandler.getFormattedEmoji(event.getJDA(), String.valueOf(me.getPerks().getPerkStyles().get(0).getSelections().get(0).getPerk())) + "**"+ getFormattedDuration((match.getGameDuration()))  + "**\n"
                        + RiotHandler.getFormattedEmoji(event.getJDA(), String.valueOf(me.getSummoner2Id()) + "_")+ RiotHandler.getFormattedEmoji(event.getJDA(), String.valueOf(RiotHandler.getFatherRune(String.valueOf(me.getPerks().getPerkStyles().get(1).getSelections().get(0).getPerk())))) + "**Ward: **" + me.getWardsPlaced() + " **Vision: **" + me.getVisionScore() + "\n"
                        + RiotHandler.getFormattedEmoji(event.getJDA(), String.valueOf(me.getItem0())) + " " + RiotHandler.getFormattedEmoji(event.getJDA(), String.valueOf(me.getItem1())) + " " + RiotHandler.getFormattedEmoji(event.getJDA(), String.valueOf(me.getItem2())) + " " + RiotHandler.getFormattedEmoji(event.getJDA(), String.valueOf(me.getItem3())) + " " + RiotHandler.getFormattedEmoji(event.getJDA(), String.valueOf(me.getItem4())) + " " + RiotHandler.getFormattedEmoji(event.getJDA(), String.valueOf(me.getItem5())) + " " + RiotHandler.getFormattedEmoji(event.getJDA(), String.valueOf(me.getItem6()));
            eb.addField(
                match.getGameName(),content, true);
            String people = RiotHandler.getFormattedEmoji(event.getJDA(), blue.get(0).getChampionName())+ RiotHandler.getFormattedEmoji(event.getJDA(), red.get(0).getChampionName())+ "\n"
                            + RiotHandler.getFormattedEmoji(event.getJDA(), blue.get(1).getChampionName())+ RiotHandler.getFormattedEmoji(event.getJDA(), red.get(1).getChampionName())+ "\n"
                            + RiotHandler.getFormattedEmoji(event.getJDA(), blue.get(2).getChampionName())+ RiotHandler.getFormattedEmoji(event.getJDA(), red.get(2).getChampionName())+ "\n"
                            + RiotHandler.getFormattedEmoji(event.getJDA(), blue.get(3).getChampionName())+ RiotHandler.getFormattedEmoji(event.getJDA(), red.get(3).getChampionName())+ "\n"
                            + RiotHandler.getFormattedEmoji(event.getJDA(), blue.get(4).getChampionName())+ RiotHandler.getFormattedEmoji(event.getJDA(), red.get(4).getChampionName())+ "\n";
            eb.addField("Random", people, true);
            eb.addBlankField(true);
            }

        event.getHook().editOriginalEmbeds(eb.build()).queue();
        

	}

    private String getFormattedDuration(int seconds){
        int S = seconds % 60;
        int H = seconds / 60;
        int M = H % 60;
        return M + "m: " + S + "s";
    } 

}
