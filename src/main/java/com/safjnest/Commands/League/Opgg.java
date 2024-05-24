package com.safjnest.commands.League;


import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.core.Bot;
import com.safjnest.model.customemoji.CustomEmojiHandler;
import com.safjnest.util.CommandsLoader;
import com.safjnest.util.DateHandler;
import com.safjnest.util.LOL.RiotHandler;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.api.regions.RegionShard;
import no.stelar7.api.r4j.basic.constants.types.lol.TeamType;
import no.stelar7.api.r4j.impl.R4J;
import no.stelar7.api.r4j.pojo.lol.match.v5.LOLMatch;
import no.stelar7.api.r4j.pojo.lol.match.v5.MatchParticipant;
import no.stelar7.api.r4j.pojo.lol.match.v5.PerkSelection;
import no.stelar7.api.r4j.pojo.lol.match.v5.PerkStyle;
import no.stelar7.api.r4j.pojo.shared.RiotAccount;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @since 1.3
 */
public class Opgg extends Command {
    /**
     * Constructor
     */
    public Opgg() {
        this.name = this.getClass().getSimpleName().toLowerCase();
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
        Button left = Button.primary("match-left", "<-");
        Button right = Button.primary("match-right", "->");
        Button center = Button.primary("match-center", "f");


        no.stelar7.api.r4j.pojo.lol.summoner.Summoner s = null;

        User theGuy = null;
        if(event.getArgs().equals("")) theGuy = event.getAuthor();    
        else if(event.getMessage().getMentions().getMembers().size() != 0) theGuy = event.getMessage().getMentions().getUsers().get(0);

        s = RiotHandler.getSummonerByArgs(event);
        if(s == null){
            event.reply("Couldn't find the specified summoner. Remember to use the tag or connect an account.");
            return;
        }
        
        
        EmbedBuilder builder = createEmbed(s, event.getJDA());
        
        if(theGuy != null && RiotHandler.getNumberOfProfile(theGuy.getId()) > 1){
            RiotAccount account = RiotHandler.getRiotApi().getAccountAPI().getAccountByPUUID(RegionShard.EUROPE, s.getPUUID());
            center = Button.primary("match-center-" + s.getAccountId() + "#" + s.getPlatform().name(), account.getName());
            center = center.asDisabled();
            event.getChannel().sendMessageEmbeds(builder.build()).addActionRow(left, center, right).queue();
            return;
        }

        event.reply(builder.build());
        
    }
    
    
    
    public static EmbedBuilder createEmbed(no.stelar7.api.r4j.pojo.lol.summoner.Summoner s , JDA jda){
        LeagueShard shard = s.getPlatform();
        RegionShard region = RiotHandler.getRegionFromServer(shard);

        RiotAccount account = RiotHandler.getRiotApi().getAccountAPI().getAccountByPUUID(region, s.getPUUID());
        EmbedBuilder eb = new EmbedBuilder();
        MatchParticipant me = null;
        LOLMatch match = null;
        R4J r4j = RiotHandler.getRiotApi();

        eb.setAuthor(account.getName() + "#" + account.getTag());
        eb.setColor(Bot.getColor());
        
        for(int i = 0; i < 5; i++){
            try {
                
                match = r4j.getLoLAPI().getMatchAPI().getMatch(region, s.getLeagueGames().get().get(i));
                if (match.getParticipants().size() == 0)
                    continue; //riot di merda che quando crasha il game lascia dati sporchi

                for(MatchParticipant mp : match.getParticipants()){
                    if(mp.getSummonerId().equals(s.getSummonerId())){
                        me = mp;
                    }
                }
                ArrayList<String> blue = new ArrayList<>();
                ArrayList<String> red = new ArrayList<>();
                for(MatchParticipant searchMe : match.getParticipants()){
                    String partecipantString = CustomEmojiHandler.getFormattedEmoji(searchMe.getChampionName()) 
                                                + " " 
                                                + searchMe.getKills() + "/" + searchMe.getDeaths() + "/" + searchMe.getAssists(); 

                    if(searchMe.getTeam() == TeamType.BLUE)
                        blue.add(partecipantString);
                    else
                        red.add(partecipantString);
                }
    
                String kda = me.getKills() + "/" + me.getDeaths()+ "/" + me.getAssists();
                String content = "";
                Instant instant = Instant.ofEpochMilli(match.getGameCreation() + match.getGameDurationAsDuration().toMillis() + 3600000*2);
                ZoneOffset offset = ZoneOffset.UTC;
                OffsetDateTime offsetDateTime = instant.atOffset(offset);
                String date = DateHandler.formatDate(offsetDateTime);
                date = "<t:" + ((match.getGameCreation()/1000) + match.getGameDurationAsDuration().getSeconds()) + ":R>";
                switch (match.getQueue()){

                    case CHERRY:
                        
                        content = CustomEmojiHandler.getFormattedEmoji(me.getChampionName()) + kda +"\n"
                        + date + " | **"+ getFormattedDuration((match.getGameDuration()))  + "**\n"
                        + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getSummoner1Id()) + "_") + CustomEmojiHandler.getFormattedEmoji("a" + String.valueOf(me.getPlayerAugment1())) + " " + CustomEmojiHandler.getFormattedEmoji("a" + String.valueOf(me.getPlayerAugment2())) + "\n"
                        + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getSummoner2Id()) + "_") + CustomEmojiHandler.getFormattedEmoji("a" + String.valueOf(me.getPlayerAugment3())) + " " + CustomEmojiHandler.getFormattedEmoji("a" + String.valueOf(me.getPlayerAugment4())) + "\n"
                        + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem0())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem1())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem2())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem3())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem4())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem5())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem6()));
                       
                        eb.addField(
                            "ARENA: " + (me.didWin() ? "WIN" : "LOSE") , content, true);

                        HashMap<String, ArrayList<String>> prova = new HashMap<>();
                        prova.put("teamscuttles", new ArrayList<>());
                        prova.put("teamporos", new ArrayList<>());
                        prova.put("teamkrugs", new ArrayList<>());
                        prova.put("teamminions", new ArrayList<>());

                        prova.put("teamsentinels", new ArrayList<>());
                        prova.put("teamgromps", new ArrayList<>());
                        prova.put("teamraptors", new ArrayList<>());
                        prova.put("teamwolves", new ArrayList<>());

                        HashMap<Integer, String> positions = new HashMap<>();
                        
                        for(MatchParticipant mt : match.getParticipants()){
                            String name = "";
                            String team = "";
                            switch (mt.getPlayerSubteamId()) {
                                case 1:
                                    team = "teamporos";
                                break;
                                case 2:
                                    team = "teamminions";
                                break;
                                case 3:
                                    team = "teamscuttles";
                                break;
                                case 4:
                                    team = "teamkrugs";
                                break;
                                case 5:
                                    team = "teamraptors";
                                break;
                                case 6:
                                    team = "teamsentinels";
                                break;
                                case 7:
                                    team = "teamwolves";
                                break;
                                case 8:
                                    team = "teamgromps";
                                break;
                            }
                            prova.get(team).add(CustomEmojiHandler.getFormattedEmoji(mt.getChampionName()) + name);
                            positions.put(mt.getPlacement(), team);
                        }
                        String blueTeam = "";
                        String redTeam = "";
                        for (int j = 1; j <= 8; j++) {
                            String team = positions.get(j);
                            String space = j % 2 == 0 ? "\n\n" : "\n";
                            if (j <= 4)
                                blueTeam += CustomEmojiHandler.getFormattedEmoji(team) + prova.get(team).get(0) + prova.get(team).get(1) + space;
                            else
                                redTeam += CustomEmojiHandler.getFormattedEmoji(team) + prova.get(team).get(0) + prova.get(team).get(1) + space;
                        }
                        eb.addField("Top 4", blueTeam, true);
                        eb.addField("Others", redTeam, true);
                    break;

                    default:
                     content = CustomEmojiHandler.getFormattedEmoji(me.getChampionName()) + kda + " | " + "**Vision: **"+ me.getVisionScore()+"\n"
                                + date  + " | ** " + getFormattedDuration((match.getGameDuration())) + "**\n"
                                + CustomEmojiHandler.getFormattedEmoji( String.valueOf(me.getSummoner1Id()) + "_") + getFormattedRunes(me, 0) + "\n"
                                + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getSummoner2Id()) + "_") + getFormattedRunes(me, 1) + "\n"
                                + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem0())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem1())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem2())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem3())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem4())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem5())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem6()));
                                eb.addField(
                                    match.getQueue().commonName() + ": " + (me.didWin() ? "WIN" : "LOSE") , content, true);
                                String blueS = "";
                                String redS = "";
                                for(int j = 0; j < 5; j++)
                                    blueS += blue.get(j) + "\n";
                                 for(int j = 0; j < 5; j++)
                                    redS += red.get(j) + "\n";
                                eb.addField("Blue Side", blueS, true);
                                eb.addField("Red Side", redS, true);
                    break;


                }

            } catch (Exception e) {
                e.printStackTrace();
                continue;
            }
        }
        return eb;
    }

    private static String getFormattedDuration(int seconds) {
        int S = seconds % 60;
        int H = seconds / 60;
        int M = H % 60;
        return M + "m: " + S + "s";
    }

    private static String getFormattedRunes(MatchParticipant me, int row) {
        String prova = "";
        PerkStyle perkS = me.getPerks().getPerkStyles().get(row);
        prova += CustomEmojiHandler.getFormattedEmoji(RiotHandler.getFatherRune(perkS.getSelections().get(0).getPerk()));
        for (PerkSelection perk : perkS.getSelections()) {
            prova += CustomEmojiHandler.getFormattedEmoji(perk.getPerk());
        }
        return prova;

    }

   
}
