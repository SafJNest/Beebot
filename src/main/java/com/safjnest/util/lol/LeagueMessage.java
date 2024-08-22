package com.safjnest.util.lol;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.safjnest.core.Bot;
import com.safjnest.model.customemoji.CustomEmojiHandler;
import com.safjnest.sql.DatabaseHandler;
import com.safjnest.sql.QueryResult;
import com.safjnest.sql.ResultRow;
import com.safjnest.util.DateHandler;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.LayoutComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.api.regions.RegionShard;
import no.stelar7.api.r4j.basic.constants.types.lol.TeamType;
import no.stelar7.api.r4j.impl.R4J;
import no.stelar7.api.r4j.pojo.lol.match.v5.LOLMatch;
import no.stelar7.api.r4j.pojo.lol.match.v5.MatchParticipant;
import no.stelar7.api.r4j.pojo.lol.match.v5.PerkSelection;
import no.stelar7.api.r4j.pojo.lol.match.v5.PerkStyle;
import no.stelar7.api.r4j.pojo.lol.spectator.SpectatorParticipant;
import no.stelar7.api.r4j.pojo.lol.summoner.Summoner;
import no.stelar7.api.r4j.pojo.shared.RiotAccount;

public class LeagueMessage {


    public static List<LayoutComponent> composeButtons(Summoner s, String user_id, String id) {
        Button left = Button.primary(id + "-left", " ").withEmoji(CustomEmojiHandler.getRichEmoji("leftarrow"));
        Button right = Button.primary(id + "-right", " ").withEmoji(CustomEmojiHandler.getRichEmoji("rightarrow"));
        Button refresh = Button.primary(id + "-refresh", " ").withEmoji(CustomEmojiHandler.getRichEmoji("refresh"));

        Button profile = Button.primary(id + "-lol", " ").withEmoji(CustomEmojiHandler.getRichEmoji("user"));
        Button opgg = Button.primary(id + "-match", " ").withEmoji(CustomEmojiHandler.getRichEmoji("list2"));
        Button livegame = Button.primary(id + "-rank", " ").withEmoji(CustomEmojiHandler.getRichEmoji("game"));

        switch (id) {
            case "lol":
                profile = profile.asDisabled().withStyle(ButtonStyle.SUCCESS);
                break;
            case "match":
                opgg = opgg.asDisabled().withStyle(ButtonStyle.SUCCESS);
                break;
            case "rank":    
                livegame = livegame.asDisabled().withStyle(ButtonStyle.SUCCESS);
                break;
        }

        RiotAccount account = LeagueHandler.getRiotAccountFromSummoner(s);
        Button center = Button.primary(id + "-center-" + s.getAccountId() + "#" + s.getPlatform().name(), account.getName());
        center = center.asDisabled();
    
        if (user_id != null && LeagueHandler.getNumberOfProfile(user_id) > 1) {
            return List.of(ActionRow.of(left, center, right), ActionRow.of(profile, opgg, livegame, refresh));

        }

        return List.of(ActionRow.of(center, profile, opgg, livegame, refresh));
    }
    
    public static EmbedBuilder getSummonerEmbed(Summoner s) {
        RiotAccount account = LeagueHandler.getRiotAccountFromSummoner(s);
        
        EmbedBuilder builder = new EmbedBuilder();
        builder.setAuthor(account.getName() + "#" + account.getTag());
        builder.setColor(Bot.getColor());
        builder.setThumbnail(LeagueHandler.getSummonerProfilePic(s));

        String userId = DatabaseHandler.getUserIdByLOLAccountId(s.getAccountId(), s.getPlatform());

        if(userId != null){
            User theGuy = Bot.getJDA().retrieveUserById(userId).complete();
            builder.addField("User:", theGuy.getName(), true);
            builder.addField("Level:", String.valueOf(s.getSummonerLevel()), true);
            builder.addBlankField(true);

            ResultRow data = DatabaseHandler.getSummonerData(userId, s.getAccountId());
            if (data.getAsBoolean("tracking")) builder.setFooter("LPs tracking enabled for the current summoner");
            else builder.setFooter("LPs tracking disabled for the current summoner");
            
        }else{
            builder.addField("Level:", String.valueOf(s.getSummonerLevel()), false);
        }
        
        builder.addField("Solo/duo Queue", LeagueHandler.getSoloQStats(s), true);
        builder.addField("Flex Queue", LeagueHandler.getFlexStats(s), true);
        String masteryString = "";
        for(int i = 1; i < 4; i++)
            masteryString += LeagueHandler.getMastery(s, i) + "\n";
        
        builder.addField("Top 3 Champs", masteryString, false); 
        builder.addField("Activity", LeagueHandler.getActivity(s), true);

        return builder;
    }

    public static List<LayoutComponent> getSummonerButtons(Summoner s, String user_id) {
        return composeButtons(s, user_id, "lol");
    }


    public static EmbedBuilder getOpggEmbed(Summoner s) {
        LeagueShard shard = s.getPlatform();
        RegionShard region = shard.toRegionShard();

        RiotAccount account = LeagueHandler.getRiotAccountFromSummoner(s);
        EmbedBuilder eb = new EmbedBuilder();
        MatchParticipant me = null;
        LOLMatch match = null;
        R4J r4j = LeagueHandler.getRiotApi();

        eb.setAuthor(account.getName() + "#" + account.getTag());
        eb.setColor(Bot.getColor());
        
        List<String> gameIds = new ArrayList<>();
        for (String ss : s.getLeagueGames().withCount(20).get()) gameIds.add(ss.split("_")[1]);
        
        QueryResult result = DatabaseHandler.getSummonerData(s.getAccountId(), gameIds.toArray(new String[0]));

        for(int i = 0; i < 5; i++){
            try {
                
                match = r4j.getLoLAPI().getMatchAPI().getMatch(region, s.getLeagueGames().withCount(20).get().get(i));
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
                    case STRAWBERRY:
                    content = CustomEmojiHandler.getFormattedEmoji(me.getChampionName()) + " Level: " +  me.getChampionLevel() + " | " + CustomEmojiHandler.getFormattedEmoji("golds") + me.getGoldEarned() +  "\n"
                    + date  + " | ** " + getFormattedDuration((match.getGameDuration())) + "**\n"
                    + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem0())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem1())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem2())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem3())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem4())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem5())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem6()));
                    eb.addField(
                        "Swarm" + ": " + (me.didWin() ? "WIN" : "LOSE") , content, true);

                    String swarmTeam = "";
                    for(MatchParticipant mt : match.getParticipants())
                        swarmTeam += CustomEmojiHandler.getFormattedEmoji(mt.getChampionName()) + " Level: " +  mt.getChampionLevel() + " | " + CustomEmojiHandler.getFormattedEmoji("golds") + mt.getGoldEarned() +  "\n";
                    
                    eb.addField("Swarm Team", swarmTeam, true);
                    eb.addBlankField(true);
                    break;

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
                    String gain = "";
                    for (ResultRow row : result) {
                        if (row.getAsLong("game_id") == match.getGameId()) {
                            gain = row.getAsInt("gain") > 0 ? "+" + row.getAsInt("gain") : String.valueOf(row.getAsInt("gain"));
                            break;
                        }
                    }
                    gain = (gain.isBlank() || gain.equals("0")) ? "" : (gain + " LP");
                    content = CustomEmojiHandler.getFormattedEmoji(me.getChampionName()) + kda + " | " + "**Vision: **"+ me.getVisionScore()+"\n"
                                + date  + " | ** " + getFormattedDuration((match.getGameDuration())) + "**\n"
                                + CustomEmojiHandler.getFormattedEmoji( String.valueOf(me.getSummoner1Id()) + "_") + getFormattedRunes(me, 0) + "\n"
                                + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getSummoner2Id()) + "_") + getFormattedRunes(me, 1) + "\n"
                                + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem0())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem1())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem2())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem3())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem4())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem5())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem6()));
                                eb.addField(
                                    match.getQueue().commonName() + ": " + (me.didWin() ? "WIN" : "LOSE") + " " + gain , content, true);
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

    public static List<LayoutComponent> getOpggButtons(Summoner s, String user_id) {
        return composeButtons(s, user_id, "match");
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
        prova += CustomEmojiHandler.getFormattedEmoji(LeagueHandler.getFatherRune(perkS.getSelections().get(0).getPerk()));
        for (PerkSelection perk : perkS.getSelections()) {
            prova += CustomEmojiHandler.getFormattedEmoji(perk.getPerk());
        }
        return prova;

    }



    public static EmbedBuilder getLivegameEmbed(Summoner summoner, List<SpectatorParticipant> spectators, List<RiotAccount> accounts) {
        RiotAccount me = null;
        for(RiotAccount a : accounts){
            if(a.getPUUID().equals(summoner.getPUUID())){
                me = a;
                break;
            }
        }
        try {
            EmbedBuilder builder = new EmbedBuilder();
            builder.setTitle(me.getName() + "#" + me.getTag() + "'s Game");
            builder.setColor(Bot.getColor());
            builder.setThumbnail(LeagueHandler.getSummonerProfilePic(summoner));
            String blueSide = "";
            String redSide = "";
            for (SpectatorParticipant partecipant : spectators) {
                RiotAccount account = accounts.stream().filter(a -> a.getPUUID().equals(partecipant.getPuuid())).findFirst().orElse(null);


                String sum = CustomEmojiHandler.getFormattedEmoji(
                        LeagueHandler.getRiotApi().getDDragonAPI().getChampion(partecipant.getChampionId()).getName())
                        + " " + account.getName().toUpperCase();
                String stats = "";
                if (summoner.getCurrentGame().getGameQueueConfig().commonName().equals("5v5 Ranked Flex Queue")) {
                    stats = LeagueHandler.getFlexStats(LeagueHandler.getSummonerBySummonerId(partecipant.getSummonerId(), summoner.getPlatform()));
                    stats = stats.substring(0, stats.lastIndexOf("P") + 1) + " | "  + stats.substring(stats.lastIndexOf(":") + 1);

                } else {
                    stats = LeagueHandler.getSoloQStats(LeagueHandler.getSummonerBySummonerId(partecipant.getSummonerId(), summoner.getPlatform()));
                    stats = stats.substring(0, stats.lastIndexOf("P") + 1) + " | " + stats.substring(stats.lastIndexOf(":") + 1);
                }

                if (partecipant.getTeam() == TeamType.BLUE) blueSide += "**" + sum + "** " + stats + "\n";
                else redSide += "**" + sum + "** " + stats + "\n";

            }
            if (summoner.getCurrentGame().getGameQueueConfig().commonName().equals("5v5 Ranked Flex Queue")) builder.addField("Ranked stats", "FLEX", false);
            else builder.addField("Ranked stats", "SOLOQ", false);

            builder.addField("**BLUE SIDE**", blueSide, false);
            builder.addField("**RED SIDE**", redSide, true);
            builder.setFooter("For every gamemode would be use the SOLOQ ranked data. Flex would be shown only if the game is a Flex game.");
            return builder;

        } catch (Exception e) {
            EmbedBuilder builder = new EmbedBuilder();
            builder.setTitle(me.getName() + "#" + me.getTag() + "'s Game");
            builder.setColor(Bot.getColor());
            builder.setThumbnail(LeagueHandler.getSummonerProfilePic(summoner));
            builder.setDescription("This user is not in a game.");
            return builder;
        }
    }

    public static StringSelectMenu getLivegameMenu(Summoner summoner, List<SpectatorParticipant> spectators, List<RiotAccount> accounts) {
        if (spectators == null || spectators.size() == 0) return null;

        ArrayList<SelectOption> options = new ArrayList<>();
        for(SpectatorParticipant p : spectators){
            RiotAccount account = LeagueHandler.getRiotApi().getAccountAPI().getAccountByPUUID(summoner.getPlatform().toRegionShard(), p.getPuuid());
            accounts.add(account);
            Emoji icon = Emoji.fromCustom(
                LeagueHandler.getRiotApi().getDDragonAPI().getChampion(p.getChampionId()).getName(), 
                Long.parseLong(CustomEmojiHandler.getEmojiId(LeagueHandler.getRiotApi().getDDragonAPI().getChampion(p.getChampionId()).getName())), false);
                
            options.add(SelectOption.of(account.getName().toUpperCase(), p.getSummonerId() + "#" + summoner.getPlatform().name()).withEmoji(icon));
        }

        return StringSelectMenu.create("rank-select")
                .setPlaceholder("Select a summoner")
                .setMaxValues(1)
                .addOptions(options)
                .build();
    }

    public static List<LayoutComponent> getLivegameButtons(Summoner s, String user_id) {
        return composeButtons(s, user_id, "rank");
    }

}
