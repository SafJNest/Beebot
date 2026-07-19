package com.safjnest.lol.message;

import com.safjnest.mongo.MongoDB;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONObject;

import java.sql.SQLException;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.core.Bot;
import com.safjnest.core.Chronos.ChronoTask;
import com.safjnest.lol.LeagueHandler;
import com.safjnest.lol.model.ChampionStatistics;
import com.safjnest.lol.model.ChampionStatistics.LaneStat;
import com.safjnest.lol.model.ChampionStatistics.Matchup;
import com.safjnest.lol.model.match.Match;
import com.safjnest.lol.model.PlayerChampionStats;
import com.safjnest.lol.model.match.Participant;
import com.safjnest.lol.tracker.Tracker;
import com.safjnest.lol.utils.ChampionUtils;
import com.safjnest.lol.utils.GameQueueTypeUtils;
import com.safjnest.lol.utils.LaneTypeUtils;
import com.safjnest.lol.utils.LeagueMessageUtils;
import com.safjnest.lol.utils.LeagueShardUtils;
import com.safjnest.lol.utils.SeasonUtils;
import com.safjnest.lol.service.ChampionStatsService;
import com.safjnest.lol.service.LeagueService;
import com.safjnest.model.customemoji.CustomEmojiHandler;
import com.safjnest.sql.QueryResult;
import com.safjnest.sql.QueryRecord;
import com.safjnest.sql.database.LeagueDB;
import com.safjnest.utils.Accumulator;
import com.safjnest.utils.DateHandler;
import com.safjnest.utils.SafJNest;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji;
import net.dv8tion.jda.api.interactions.InteractionHook;
import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.basic.constants.types.lol.TeamType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierDivisionType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierType;
import no.stelar7.api.r4j.pojo.lol.championmastery.ChampionMastery;
import no.stelar7.api.r4j.pojo.lol.league.LeagueEntry;
import no.stelar7.api.r4j.pojo.lol.match.v5.ChampionBan;
import no.stelar7.api.r4j.pojo.lol.match.v5.LOLMatch;
import no.stelar7.api.r4j.pojo.lol.match.v5.MatchParticipant;
import no.stelar7.api.r4j.pojo.lol.match.v5.MatchTeam;
import no.stelar7.api.r4j.pojo.lol.shared.BannedChampion;
import no.stelar7.api.r4j.pojo.lol.spectator.SpectatorGameInfo;
import no.stelar7.api.r4j.pojo.lol.spectator.SpectatorParticipant;
import no.stelar7.api.r4j.pojo.lol.staticdata.champion.StaticChampion;
import no.stelar7.api.r4j.pojo.lol.summoner.Summoner;
import no.stelar7.api.r4j.pojo.shared.RiotAccount;

public class LeagueMessage {

    public static final String BUTTON_ID_PREFIX = "lol";

    private static Object[] build(String userId, Summoner summoner, String puuid, LeagueMessageParameter parameter) {
        MessageEmbed embed = null;
        List<MessageTopLevelComponent> components = new ArrayList<>();
        switch (parameter.getMessageType()) {
            case PROFILE:
                embed = getSummonerEmbed(summoner, parameter).build();
                components = getSummonerButtons(summoner, userId, parameter);
                break;
            case LIVEGAME:
                SpectatorGameInfo liveGame = LeagueService.getSpectatorGame(summoner.getPUUID(), summoner.getPlatform());
                List<SpectatorParticipant> users = liveGame != null ? liveGame.getParticipants() : null;
                StringSelectMenu menu = LeagueMessage.getLivegameMenu(summoner, users);

                embed = LeagueMessage.getLivegameEmbed(summoner, liveGame, users).build();
                components = new ArrayList<>(composeButtons(summoner, userId != null ? userId : null, new LeagueMessageParameter(LeagueMessageType.LIVEGAME)));
                if (menu != null) 
                    components.add(0, ActionRow.of(menu));
                
                break;
            case OPGG:
                if (parameter.getMatch() != null) {
                    embed = getOpggEmbedMatch(summoner, parameter.getMatch()).build();
                    components = getOpggButtons(summoner, userId, parameter);
                } else {
                    List<LOLMatch> matches = loadMatchesParallel(summoner, parameter.getQueueType(), parameter.getOffset());
                    embed = getOpggEmbed(summoner, parameter, matches).build();
                    components = getOpggButtons(summoner, userId, parameter, matches);
                }
                break;
            case OVERVIEW:
            case MATCHUP:
            case OVERVIEW_PING:
            case OVERVIEW_OBJECTIVES:
            case OVERVIEW_CHAMPIONS:
            case OVERVIEW_OPGG:
                embed = buildEmbedChampion(userId, summoner, puuid, parameter);
                components = getChampionButtons(userId, summoner, parameter);
                break;
            case CHAMPIONS_BY_WINRATE:
            case CHAMPIONS_BY_PICKRATE:
            case CHAMPIONS_BY_BANRATE:
                List<ChampionStatistics> champions = ChampionStatsService.getAll(parameter.toFilter()).values().stream().toList();
                embed = buildEmbedChampions(parameter, champions);
                components = getChampionsButtons(parameter);
                break;
            default:
                break;
        }
        return new Object[]{embed, components};     
    }

    private static List<MessageTopLevelComponent> getChampionsButtons(LeagueMessageParameter parameter) {
        List<MessageTopLevelComponent> rows = new ArrayList<>();
    
        Button winrate = Button.primary("lol-type-" + LeagueMessageType.CHAMPIONS_BY_WINRATE,  "Winrate");
        Button pickrate = Button.primary("lol-type-" + LeagueMessageType.CHAMPIONS_BY_PICKRATE, "Pickrate");
        Button banrate = Button.primary("lol-type-" + LeagueMessageType.CHAMPIONS_BY_BANRATE,  "Banrate");
    
        String stateKey = parameter.toFilter().toStateKey();
        Button left = Button.primary(BUTTON_ID_PREFIX + "-leftpage-"  + parameter.getOffset() + "-" + stateKey, " ").withEmoji(CustomEmojiHandler.getRichEmoji("leftarrow"));
        Button right = Button.primary(BUTTON_ID_PREFIX + "-rightpage-" + parameter.getOffset() + "-" + stateKey, " ").withEmoji(CustomEmojiHandler.getRichEmoji("rightarrow"));
        if (parameter.getOffset() <= 0) left = left.asDisabled();
    
        List<SelectOption> tierOptions = new ArrayList<>();
        tierOptions.add(SelectOption.of("All", "ALL").withDefault(parameter.getRank() == null));
        for (TierType tier : TierType.values()) {
            tierOptions.add(SelectOption.of(tier.name(), tier.name())
                .withEmoji(CustomEmojiHandler.getRichEmoji(tier.name()))
                .withDefault(parameter.getRank() == tier));
        }
    
        List<SelectOption> shardOptions = new ArrayList<>();
        for (LeagueShard shard : LeagueShardUtils.getActives()) {
            RichCustomEmoji emoji = CustomEmojiHandler.getRichEmoji(shard.getRealmValue().toUpperCase() + "_server");
            SelectOption opt = SelectOption.of(shard.name(), shard.name()).withDefault(parameter.getRegion() == shard);
            shardOptions.add(emoji != null ? opt.withEmoji(emoji) : opt);
        }
    
        switch (parameter.getMessageType()) {
            case CHAMPIONS_BY_PICKRATE -> pickrate = pickrate.withStyle(ButtonStyle.SUCCESS);
            case CHAMPIONS_BY_BANRATE -> banrate = banrate.withStyle(ButtonStyle.SUCCESS);
            default -> winrate = winrate.withStyle(ButtonStyle.SUCCESS);
        }
    
        rows.add(LeagueMessageUtils.getOpggQueueTypeButtons(ButtonStyle.SECONDARY, parameter.getQueueType()));
        if (parameter.getQueueType() == null || GameQueueTypeUtils.hasLane(parameter.getQueueType()))
            rows.add(LeagueMessageUtils.getLaneComponents(parameter.getLaneType()));
        rows.add(ActionRow.of(
            StringSelectMenu.create(BUTTON_ID_PREFIX + "-tier")
                .setPlaceholder("Rank")
                .setMaxValues(1)
                .addOptions(tierOptions).build()
            )
        );
        rows.add(ActionRow.of(
            StringSelectMenu.create(BUTTON_ID_PREFIX + "-shard")
                .setPlaceholder("Region")
                .setMinValues(0)
                .setMaxValues(1)
                .addOptions(shardOptions)
                .build()
            )
        );
        rows.add(ActionRow.of(left, right, winrate, pickrate, banrate));
    
        return rows;
    }

    private static MessageEmbed buildEmbedChampions(LeagueMessageParameter parameter, List<ChampionStatistics> champions) {
        EmbedBuilder  eb   = new EmbedBuilder();
        StringBuilder desc = new StringBuilder();
    
        int pageSize  = parameter.getMessageType().getPageItem();
        LaneType lane = parameter.getLaneType();
        int opponent  = parameter.getOpponent();
    
        if (parameter.getPatch()     != null) desc.append("Patch `").append(parameter.getPatch()).append("`\n");
        if (parameter.getRegion()    != null) desc.append(LeagueShardUtils.getRegionFlag(parameter.getRegion())).append(" ").append(parameter.getRegion()).append("\n");
        if (parameter.getRank()      != null) desc.append(CustomEmojiHandler.getFormattedEmoji(parameter.getRank().toString())).append(" ").append(SafJNest.capitalize(parameter.getRank().toString())).append("\n");
        if (parameter.getQueueType() != null) desc.append(GameQueueTypeUtils.getMapEmoji(parameter.getQueueType())).append(" ").append(GameQueueTypeUtils.prettyName(parameter.getQueueType())).append("\n");
        if (parameter.getLaneType()  != null) desc.append(LaneTypeUtils.getLaneTypeEmoji(lane)).append(" ").append(LaneTypeUtils.getPrettyName(lane)).append("\n");
        if (opponent != 0) {
            StaticChampion opponentChampion = ChampionUtils.getChampion(opponent);
            if (opponentChampion != null)
                desc.append("Against ").append(CustomEmojiHandler.getFormattedEmoji(opponentChampion.getName())).append(" ").append(opponentChampion.getName()).append("\n");
        }
    
        desc.append("\n");
    
        Comparator<ChampionStatistics> comparator = switch (parameter.getMessageType()) {
            case CHAMPIONS_BY_WINRATE -> Comparator.comparingDouble(s -> getChampionWinrate(s, lane, opponent));
            case CHAMPIONS_BY_PICKRATE -> Comparator.comparingDouble(s -> getChampionPickrate(s, lane, opponent));
            case CHAMPIONS_BY_BANRATE -> Comparator.comparingDouble(s -> s.banrate());
            default -> Comparator.comparingDouble(s -> getChampionWinrate(s, lane, opponent));
        };
    
        List<ChampionStatistics> filtered = champions.stream()
            .filter(s -> canShowChampion(s, lane, opponent))
            .sorted(comparator.reversed())
            .toList();

        int offset = Math.max(0, parameter.getOffset());
        if (offset >= filtered.size() && !filtered.isEmpty())
            offset = ((filtered.size() - 1) / pageSize) * pageSize;
        parameter.setOffset(offset);

        filtered.stream()
            .skip(offset)
            .limit(pageSize)
            .forEach(s -> {
                StaticChampion champion = ChampionUtils.getChampion(s.filter().champion());
        
                if (champion == null) {
                    desc.append(s.filter().champion()).append("\n");
                    return;
                }

                Matchup matchup = opponent != 0 ? getOpponentMatchup(s, opponent, lane) : null;
                LaneStat ls = lane != null ? s.getLaneStat(lane) : null;
        
                desc.append(CustomEmojiHandler.getFormattedEmoji(champion.getName()))
                    .append(" **").append(champion.getName()).append("**: ");

                if (matchup != null) {
                    desc.append(matchup.prettyMatches()).append(" games\n")
                        .append("`Winrate ").append(matchup.prettyWinrate())
                        .append(" | Banrate ").append(s.prettyBanrate())
                        .append("`\n");
                }
                else if (ls != null) {
                    desc.append(ls.prettyGames()).append(" games\n")
                        .append("`Winrate ").append(ls.prettyWinrate())
                        .append(" | Pickrate ").append(ls.prettyPickrate(s.games()))
                        .append(" | Banrate ").append(s.prettyBanrate())
                        .append("`\n");
                }
                else {
                    desc.append(s.picks()).append(" games\n")
                        .append("`Winrate ").append(s.prettyWinrate())
                        .append(" | Pickrate ").append(s.prettyPickrate())
                        .append(" | Banrate ").append(s.prettyBanrate())
                        .append("`\n");
                }
            }
        );

        if (filtered.isEmpty())
            desc.append("Not enough data for this filter.");
        
        int total   = filtered.size();
        int pages   = Math.max(1, (int) Math.ceil((double) total / pageSize));
        int curPage = offset / pageSize + 1;
    
        eb.setColor(Bot.getColor());
        eb.setTitle("Champion Tier List");
        eb.setDescription(desc.toString());
        eb.setFooter("Page " + curPage + " / " + pages + " · " + total + " champions");
    
        return eb.build();
    }

    private static boolean canShowChampion(ChampionStatistics stats, LaneType lane, int opponent) {
        if (opponent != 0)
            return getOpponentMatchup(stats, opponent, lane) != null;
        if (lane != null)
            return stats.getLaneStat(lane) != null;
        return stats.picks() > 0;
    }

    private static double getChampionWinrate(ChampionStatistics stats, LaneType lane, int opponent) {
        Matchup matchup = opponent != 0 ? getOpponentMatchup(stats, opponent, lane) : null;
        if (matchup != null) return matchup.winrate();
        LaneStat laneStat = lane != null ? stats.getLaneStat(lane) : null;
        return laneStat != null ? laneStat.winrate() : stats.winrate();
    }

    private static double getChampionPickrate(ChampionStatistics stats, LaneType lane, int opponent) {
        Matchup matchup = opponent != 0 ? getOpponentMatchup(stats, opponent, lane) : null;
        if (matchup != null) return matchup.matches();
        LaneStat laneStat = lane != null ? stats.getLaneStat(lane) : null;
        return laneStat != null ? laneStat.getPickrate(stats.games()) : stats.pickrate();
    }

    private static Matchup getOpponentMatchup(ChampionStatistics stats, int opponent, LaneType lane) {
        if (opponent == 0) return null;
        Matchup matchup = stats.getOpponentMatchup(opponent, lane);
        if (matchup != null || lane != null) return matchup;

        int matches = 0;
        double wins = 0;
        for (Map.Entry<ChampionStatistics.MatchupKey, Matchup> entry : stats.matchups().entrySet()) {
            if (entry.getKey().champion() != opponent) continue;
            matches += entry.getValue().matches();
            wins += entry.getValue().matches() * entry.getValue().winrate();
        }
        if (matches == 0) return null;
        return new Matchup(opponent, matches, wins / matches);
    }

    private static List<LOLMatch> loadMatchesParallel(Summoner s, GameQueueType queue, int offset) {
        List<String> gameIds = getMatchIds(s, queue, offset);
        return gameIds.stream()
            .limit(5)
            .parallel()
            .map(id -> {
                try { return LeagueService.getMatch(id, s.getPlatform()); }
                catch (Exception e) { return null; }
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }
    
    @SuppressWarnings("unchecked")
    public static void send(InteractionHook hook, String userId, Summoner summoner, String puuid, LeagueMessageParameter parameter) {
        Object[] built = build(userId, summoner, puuid, parameter);
        MessageEmbed embed = (MessageEmbed) built[0];
        List<MessageTopLevelComponent> components = (List<MessageTopLevelComponent>) built[1];
        hook.editOriginalEmbeds(embed).setComponents(components).queue();
    }

    @SuppressWarnings("unchecked")
    public static void send(CommandEvent event, String userId, Summoner summoner, String puuid, LeagueMessageParameter parameter) {
        Object[] built = build(userId, summoner, puuid, parameter);
        MessageEmbed embed = (MessageEmbed) built[0];
        List<MessageTopLevelComponent> components = (List<MessageTopLevelComponent>) built[1];
        event.getChannel().sendMessageEmbeds(embed).setComponents(components).queue();
    }

    @SuppressWarnings("unchecked")
    public static void edit(Message message, String userId, Summoner summoner, String puuid, LeagueMessageParameter parameter) {
        Object[] built = build(userId, summoner, puuid, parameter);
        MessageEmbed embed = (MessageEmbed) built[0];
        List<MessageTopLevelComponent> components = (List<MessageTopLevelComponent>) built[1];
        message.editMessageEmbeds(embed).setComponents(components).queue();   
    }

    public static List<MessageTopLevelComponent> composeButtons(Summoner s, String user_id, LeagueMessageParameter parameter) {
        Button left = Button.primary(BUTTON_ID_PREFIX + "-left", " ").withEmoji(CustomEmojiHandler.getRichEmoji("leftarrow"));
        Button right = Button.primary(BUTTON_ID_PREFIX + "-right", " ").withEmoji(CustomEmojiHandler.getRichEmoji("rightarrow"));
        Button refresh = Button.primary(BUTTON_ID_PREFIX + "-refresh", " ").withEmoji(CustomEmojiHandler.getRichEmoji("refresh"));

        Button profile = Button.primary(BUTTON_ID_PREFIX + "-type-profile", " ").withEmoji(CustomEmojiHandler.getRichEmoji("user"));
        Button opgg = Button.primary(BUTTON_ID_PREFIX + "-type-opgg", " ").withEmoji(CustomEmojiHandler.getRichEmoji("list2"));
        Button livegame = Button.primary(BUTTON_ID_PREFIX + "-type-livegame", " ").withEmoji(CustomEmojiHandler.getRichEmoji("game"));
        Button champ = Button.primary(BUTTON_ID_PREFIX + "-type-overview", " ").withEmoji(CustomEmojiHandler.getRichEmoji("graph"));

        switch (parameter.getMessageType()) {
            case PROFILE:
                profile = profile.asDisabled().withStyle(ButtonStyle.SUCCESS);
                break;
            case OPGG:
                opgg = opgg.withStyle(ButtonStyle.SUCCESS);
                break;
            case LIVEGAME:
                livegame = livegame.asDisabled().withStyle(ButtonStyle.SUCCESS);
                break;
            default:
                break;
        }

        RiotAccount account = LeagueService.getRiotAccountFromSummoner(s);
        Button center = Button.primary(BUTTON_ID_PREFIX + "-center-" + s.getPUUID() + "#" + s.getPlatform().name(), account.getName());
        center = center.asDisabled();

        if (user_id != null && LeagueHandler.getNumberOfProfile(user_id) > 1) 
            return List.of(ActionRow.of(profile, opgg, livegame, champ), ActionRow.of(left, center, right, refresh));
        return List.of(ActionRow.of(profile, opgg, livegame, champ), ActionRow.of(center, refresh));
    }

//     ▄████████ ███    █▄    ▄▄▄▄███▄▄▄▄     ▄▄▄▄███▄▄▄▄    ▄██████▄  ███▄▄▄▄      ▄████████    ▄████████
//    ███    ███ ███    ███ ▄██▀▀▀███▀▀▀██▄ ▄██▀▀▀███▀▀▀██▄ ███    ███ ███▀▀▀██▄   ███    ███   ███    ███
//    ███    █▀  ███    ███ ███   ███   ███ ███   ███   ███ ███    ███ ███   ███   ███    █▀    ███    ███
//    ███        ███    ███ ███   ███   ███ ███   ███   ███ ███    ███ ███   ███  ▄███▄▄▄      ▄███▄▄▄▄██▀
//  ▀███████████ ███    ███ ███   ███   ███ ███   ███   ███ ███    ███ ███   ███ ▀▀███▀▀▀     ▀▀███▀▀▀▀▀
//           ███ ███    ███ ███   ███   ███ ███   ███   ███ ███    ███ ███   ███   ███    █▄  ▀███████████
//     ▄█    ███ ███    ███ ███   ███   ███ ███   ███   ███ ███    ███ ███   ███   ███    ███   ███    ███
//   ▄████████▀  ████████▀   ▀█   ███   █▀   ▀█   ███   █▀   ▀██████▀   ▀█   █▀    ██████████   ███    ███
//                                                                                              ███    ███

    public static EmbedBuilder getSummonerEmbed(Summoner s, LeagueMessageParameter parameter) {
        RiotAccount account = LeagueService.getRiotAccountFromSummoner(s);

        EmbedBuilder builder = new EmbedBuilder();
        builder.setAuthor(account.getName() + "#" + account.getTag(), null, LeagueHandler.getSummonerProfilePic(s));
        builder.setColor(Bot.getColor());
        builder.setThumbnail(LeagueHandler.getSummonerProfilePic(s));

        String description = "Summoner is level **" + s.getSummonerLevel() + "** on " + LeagueShardUtils.getRegionFlag(s.getPlatform()) + s.getPlatform().getRealmValue() + " server.";
        builder.setDescription(description);

        builder.addField("Solo/duo", LeagueHandler.getSoloQStats(s), true);
        builder.addField("Flex", LeagueHandler.getFlexStats(s), true);

        ((ChronoTask) () -> {
            LeagueDB.addLOLAccount(s);
        }).queue();


        String masteryString = "";
        for(int i = 1; i < 4; i++)
            masteryString += LeagueHandler.getMastery(s, i) + "\n";

        builder.addField("Highest Masteries", masteryString, false);


        QueryResult advanceData = LeagueService.getAdvancedLOLData(s.getPUUID(), s.getPlatform(), parameter.getTimeStart(), parameter.getTimeEnd(), parameter.getQueueType());

        if (!advanceData.isEmpty()) {
            LinkedHashMap<LaneType, String> laneStats = new LinkedHashMap<>();
            for (String stats : advanceData.arrayColumn("lanes_played")) {
                String[] lanes = stats.split(",");
                for (String lane : lanes) {
                    lane = lane.trim();
                    LaneType laneType = LaneType.valueOf(lane.split("-")[0]);
                    laneStats.merge(laneType, lane.split("-")[1] + "-" + lane.split("-")[2], (oldValue, newValue) -> {
                        String[] oldStats = oldValue.split("-");
                        String[] newStats = newValue.split("-");
                        int totalWins = Integer.parseInt(oldStats[0]) + Integer.parseInt(newStats[0]);
                        int totalLosses = Integer.parseInt(oldStats[1]) + Integer.parseInt(newStats[1]);
                        return totalWins + "-" + totalLosses;
                    });
                }
            }

            laneStats = laneStats.entrySet()
                .stream()
                .sorted((entry1, entry2) -> {
                    String[] stats1 = entry1.getValue().split("-");
                    String[] stats2 = entry2.getValue().split("-");
                    int totalGames1 = Integer.parseInt(stats1[0]) + Integer.parseInt(stats1[1]);
                    int totalGames2 = Integer.parseInt(stats2[0]) + Integer.parseInt(stats2[1]);
                    return Integer.compare(totalGames2, totalGames1);
                })
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    Map.Entry::getValue,
                    (e1, e2) -> e1,
                    LinkedHashMap::new
                ));

            String laneString = "";
            for (LaneType lane : laneStats.keySet()) {
                String wins = laneStats.get(lane).split("-")[0];
                String losses = laneStats.get(lane).split("-")[1];
                int games = Integer.valueOf(wins) + Integer.valueOf(losses);

                if (lane == LaneType.NONE) 
                    continue;

                String percent = String.format("%.2f", Double.parseDouble(wins) * 100 / (Double.parseDouble(wins) + Double.parseDouble(losses)));
                laneString += LaneTypeUtils.getLaneTypeEmoji(lane) + " " + LaneTypeUtils.getPrettyName(lane) + " " + games + " games\n`(" +  wins + "W/" + losses + "L) - " + percent +"% WR`\n";
            }

            if (parameter.getQueueType() == null) {
                QueryResult gameData = MongoDB.getAllGamesForAccount(s.getPUUID(), parameter.getTimeStart(), parameter.getTimeEnd());
                LinkedHashMap<GameQueueType, String> gameTypeStats = new LinkedHashMap<>();
                for (QueryRecord row : gameData) {
                    GameQueueType type = row.getAsGameQueueType("queue");
                    boolean win = row.getAsBoolean("win");
    
                    String stats = gameTypeStats.getOrDefault(type, "0-0");
                    int wins = Integer.valueOf(stats.split("-")[0]);
                    int losses = Integer.valueOf(stats.split("-")[1]);
    
                    if (win) wins++;
                    else losses++;
    
                    gameTypeStats.put(type, wins + "-" + losses);
                }
    
                gameTypeStats = gameTypeStats.entrySet()
                    .stream()
                    .sorted((entry1, entry2) -> {
                        String[] stats1 = entry1.getValue().split("-");
                        String[] stats2 = entry2.getValue().split("-");
                        int wins1 = Integer.parseInt(stats1[0]);
                        int losses1 = Integer.parseInt(stats1[1]);
                        int totalGames1 = wins1 + losses1;
                
                        int wins2 = Integer.parseInt(stats2[0]);
                        int losses2 = Integer.parseInt(stats2[1]);
                        int totalGames2 = wins2 + losses2;
                
                        if (totalGames1 != totalGames2) {
                            return Integer.compare(totalGames2, totalGames1);
                        }
                
                        double winRate1 = (totalGames1 > 0) ? (double) wins1 / totalGames1 : 0;
                        double winRate2 = (totalGames2 > 0) ? (double) wins2 / totalGames2 : 0;
                        return Double.compare(winRate2, winRate1);
                    })
                    .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                    ));
    
                String gameString = "";
                int count = 0;
                int otherWins = 0;
                int otherLosses = 0;         
                for (GameQueueType game : gameTypeStats.keySet()) {
                    String wins = gameTypeStats.get(game).split("-")[0];
                    String losses = gameTypeStats.get(game).split("-")[1];
                    int games = Integer.valueOf(wins) + Integer.valueOf(losses);
                
                    String percent = String.format("%.2f", Double.parseDouble(wins) * 100 / (Double.parseDouble(wins) + Double.parseDouble(losses)));
                
                    if (count < 4) {
                        gameString += GameQueueTypeUtils.getMapEmoji(game) + " " + GameQueueTypeUtils.prettyName(game) + " " + games + " games\n`(" + wins + "W/" + losses + "L) - " + percent + "% WR`\n";
                    } else {
                        otherWins += Integer.valueOf(wins);
                        otherLosses += Integer.valueOf(losses);
                    }
                    count++;
                }
                
                if (otherWins > 0 || otherLosses > 0) {
                    int otherGames = otherWins + otherLosses;
                    String otherPercent = String.format("%.2f", (double) otherWins * 100 / otherGames);
                    gameString += CustomEmojiHandler.getFormattedEmoji("special_mode") + "Others " + otherGames + " games\n`(" + otherWins + "W/" + otherLosses + "L) - " + otherPercent + "% WR`\n";
                }
                builder.addField("Games", gameString, true);
                builder.addField("Roles", laneString , true);
            }
            else {                
                builder.addField("Games", laneString , false);
            }

            HashMap<Integer, ChampionMastery> masteries = LeagueHandler.getMastery(s);
            String champStats = "";
            for (int i = 0; i < 6 && i < advanceData.size(); i++) {
                QueryRecord row = advanceData.get(i);
                ChampionMastery mastery = masteries.get(row.getAsInt("champion"));
                champStats += LeagueMessageUtils.formatAdvancedData(row, mastery);
            }
            builder.addField("Champions", champStats, false);
        }
        builder = LeagueHandler.getActivity(builder, s);

        return builder;
    }

    public static List<MessageTopLevelComponent> getSummonerButtons(Summoner s, String user_id, LeagueMessageParameter parameter) {
        int index = 0;

        List<MessageTopLevelComponent> buttons = new ArrayList<>(composeButtons(s, user_id, parameter));
        if (parameter.getOpggMenu() != null) {
            buttons.add(index, ActionRow.of(parameter.getOpggMenu()));
            index++;
        }
        if (parameter.getLivegameMenu() != null) {
            buttons.add(index, ActionRow.of(parameter.getLivegameMenu()));
            return buttons;
        }

        long[] time = SeasonUtils.getCurrentSplitRange();
        long[] previousTime = SeasonUtils.getPreviousSplitRange();

        Button soloQ = Button.secondary("lol-queue-" + GameQueueType.TEAM_BUILDER_RANKED_SOLO, "Solo/Duo");
        Button flex = Button.secondary("lol-queue-" + GameQueueType.RANKED_FLEX_SR, "Flex");
        Button draft = Button.secondary("lol-queue-" + GameQueueType.TEAM_BUILDER_DRAFT_UNRANKED_5X5, "Draft");
        Button currentModeButton = Button.secondary("lol-queue-" + GameQueueType.CHERRY, "Arena");

        if (parameter.getQueueType() != null) {
            switch (parameter.getQueueType()) {
                case TEAM_BUILDER_RANKED_SOLO:
                    soloQ = soloQ.withStyle(ButtonStyle.SUCCESS);
                    break;
                case RANKED_FLEX_SR:
                    flex = flex.withStyle(ButtonStyle.SUCCESS);
                    break;
                case TEAM_BUILDER_DRAFT_UNRANKED_5X5:
                    draft = draft.withStyle(ButtonStyle.SUCCESS);
                    break;
                case CHERRY:
                case ULTBOOK:
                case SWIFTPLAY:
                    currentModeButton = currentModeButton.withStyle(ButtonStyle.SUCCESS);
                    break;
                default:
                    break;
            }
        }
            
        Button allSeason = Button.secondary("lol-season-all", "General");
        Button currentSplit = Button.secondary("lol-season-current", "Current Split");
        Button previousSplit = Button.secondary("lol-season-previous", "Previous Split");

        if (parameter.getTimeStart() == 0) allSeason = allSeason.withStyle(ButtonStyle.SUCCESS);
        else if (parameter.getTimeStart() == time[0]) currentSplit = currentSplit.withStyle(ButtonStyle.SUCCESS);
        else if (parameter.getTimeStart() == previousTime[0] && parameter.getTimeEnd() == previousTime[1]) previousSplit = previousSplit.withStyle(ButtonStyle.SUCCESS);

        buttons.add(index, ActionRow.of(soloQ, flex, draft, currentModeButton));
        index++;
        buttons.add(index, ActionRow.of(allSeason, currentSplit, previousSplit));
        index++;
        

        return buttons;
    }

//   ▄██████▄     ▄███████▄    ▄██████▄     ▄██████▄
//  ███    ███   ███    ███   ███    ███   ███    ███
//  ███    ███   ███    ███   ███    █▀    ███    █▀
//  ███    ███   ███    ███  ▄███         ▄███
//  ███    ███ ▀█████████▀  ▀▀███ ████▄  ▀▀███ ████▄
//  ███    ███   ███          ███    ███   ███    ███
//  ███    ███   ███          ███    ███   ███    ███
//   ▀██████▀   ▄████▀        ████████▀    ████████▀
//

    public static StringSelectMenu getOpggMenu(Summoner summoner, LeagueMessageParameter parameter) {
        List<LOLMatch> matches = loadMatchesParallel(summoner, parameter.getQueueType(), parameter.getOffset());
        return getOpggMenu(summoner, matches, parameter);
    }

    public static StringSelectMenu getOpggMenu(Summoner summoner, List<LOLMatch> matches, LeagueMessageParameter parameter) {
        ArrayList<SelectOption> options = new ArrayList<>();
        for (LOLMatch match : matches) {
            try {
                if (match.getParticipants().size() == 0) continue;

                MatchParticipant me = null;
                for (MatchParticipant mp : match.getParticipants())
                    if (mp.getPuuid().equals(summoner.getPUUID()))
                        me = mp;

                Emoji icon = ChampionUtils.getEmojiByChampion(me.getChampionId());

                String label = match.getGameDurationAsDuration().toMinutes() + " minutes " + GameQueueTypeUtils.prettyName(match.getQueue());
                String description = "As " + me.getChampionName() + " (" + me.getKills() + "/" + me.getDeaths() + "/" + me.getAssists() + " " + me.getTotalMinionsKilled() + " CS)";

                boolean isDefault = parameter.getMatch() != null && (parameter.getMatch().getGameId() == match.getGameId());
                options.add(SelectOption.of(label, summoner.getPlatform().name() + "_" + match.getGameId() + "#" + summoner.getPUUID()).withEmoji(icon).withDescription(description).withDefault(isDefault));
            } catch (Exception e) {
                continue;
            }
        }

        if (options.isEmpty()) return null;

        return StringSelectMenu.create(LeagueMessage.BUTTON_ID_PREFIX + "-opggselect")
                .setPlaceholder("Select a game")
                .setMaxValues(1)
                .addOptions(options)
                .build();
    }

    public static EmbedBuilder getOpggEmbedMatch(Summoner s, LOLMatch match) {
        MatchParticipant me = null;
        for(MatchParticipant mp : match.getParticipants())
            if(mp.getPuuid().equals(s.getPUUID()))
                me = mp;

        EmbedBuilder eb = new EmbedBuilder();
        eb.setAuthor(me.getRiotIdName() + "#" + me.getRiotIdTagline(), null, LeagueHandler.getSummonerProfilePic(s));
        eb.setColor(Bot.getColor());
        eb.setTitle(GameQueueTypeUtils.prettyName(match.getQueue()));
        eb.setDescription((me.didWin() ? "Win" : "Lose") + " as " + CustomEmojiHandler.getFormattedEmoji(me.getChampionName()) + " " + me.getChampionName() + " in " + match.getGameDurationAsDuration().toMinutes() + " minutes");

        HashMap<MatchParticipant, HashMap<String, String>> totalStats = new HashMap<>();
        HashMap<TeamType, HashMap<String, String>> teamStats = new HashMap<>();
        TeamType blue = TeamType.BLUE;
        TeamType red = TeamType.RED;

        teamStats.put(blue, new HashMap<>());
        teamStats.put(red, new HashMap<>());

        double totalKill = 0;
        double totalCreeps = 0;

        String killParticipation = "";
        String csPerMin = "";
        String personalStatsTxt = "";

        HashMap<String, String> personalstats = new HashMap<>();

        String build = "";


        switch (match.getQueue()) {
            case CHERRY:
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
                    String rank = LeagueHandler.getRankIcon(LeagueHandler.getRankEntry(mt.getPuuid(), match.getPlatform()));
                    String name = "**" + mt.getRiotIdName() + "#" + mt.getRiotIdTagline() + "**" + rank;
                    String score = "`" + mt.getKills() + "/" + mt.getDeaths() + "/" + mt.getAssists() + "`";

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
                    prova.get(team).add(CustomEmojiHandler.getFormattedEmoji(mt.getChampionName()) + name + "\n" + score);
                    positions.put(mt.getPlacement(), team);

                    totalStats.put(mt, new HashMap<>());
                    totalStats.get(mt).put("damageDealt", String.valueOf(mt.getTotalDamageDealtToChampions()));
                    totalStats.get(mt).put("damageTaken", String.valueOf(mt.getTotalDamageTaken()));

                    TeamType currentTeam = me.getPlayerSubteamId() == mt.getPlayerSubteamId() ? TeamType.BLUE : TeamType.RED;
                    teamStats.get(currentTeam).put("kills", String.valueOf(Integer.valueOf(teamStats.get(currentTeam).getOrDefault("kills", "0")) + mt.getKills()));
                    teamStats.get(currentTeam).put("damageDealt", String.valueOf(Integer.valueOf(teamStats.get(currentTeam).getOrDefault("damageDealt", "0")) + mt.getTotalDamageDealtToChampions()));
                    teamStats.get(currentTeam).put("damageTaken", String.valueOf(Integer.valueOf(teamStats.get(currentTeam).getOrDefault("damageTaken", "0")) + mt.getTotalDamageTaken()));

                }

                for (int j = 1; j <= 8; j++) {
                    String team = positions.get(j);
                    String field = CustomEmojiHandler.getFormattedEmoji(team) + " " + j + "th positon";
                    String value = prova.get(team).get(0) + "\n" + prova.get(team).get(1);
                    eb.addField(field, value, true);
                }

                totalKill = Double.valueOf(teamStats.get(me.getTeam()).get("kills")) == 0 ? 1 : Double.valueOf(teamStats.get(me.getTeam()).get("kills"));
                killParticipation = String.format("%.1f", (Double.valueOf(me.getKills()) + Double.valueOf(me.getAssists())) / totalKill * 100);
                csPerMin = String.format("%.1f", totalCreeps / Double.valueOf(match.getGameDurationAsDuration().toMinutes()));
                personalstats = totalStats.get(me);
                personalStatsTxt = "**KDA**: " + me.getKills() + "/" + me.getDeaths() + "/" + me.getAssists() + " (" +  killParticipation + "% kill participation)\n"
                                        + "**Damage Dealt to champion**: " + LeagueMessageUtils.formatNumber(personalstats.get("damageDealt")) + " (" + LeagueMessageUtils.getPosition(totalStats, personalstats, "damageDealt") + "th in the game)\n"
                                        + "**Damage Taken**: " + LeagueMessageUtils.formatNumber(personalstats.get("damageTaken")) + " (" + LeagueMessageUtils.getPosition(totalStats, personalstats, "damageTaken") + "th in the game)\n";

                eb.addField("Personal Stats", personalStatsTxt, false);

                build = CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getSummoner1Id()) + "_") + CustomEmojiHandler.getFormattedEmoji("a" + String.valueOf(me.getPlayerAugment1())) + " " + CustomEmojiHandler.getFormattedEmoji("a" + String.valueOf(me.getPlayerAugment2())) + "\n"
                        + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getSummoner2Id()) + "_") + CustomEmojiHandler.getFormattedEmoji("a" + String.valueOf(me.getPlayerAugment3())) + " " + CustomEmojiHandler.getFormattedEmoji("a" + String.valueOf(me.getPlayerAugment4())) + "\n"
                        + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem0())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem1())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem2())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem3())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem4())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem5())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem6()));
                eb.addField("Build", build, false);
                break;
            default:
                String blueSide = "";
                String redSide = "";

                String lpLabel = "";
                QueryResult result = LeagueService.getSummonerData(s.getPUUID(), s.getPlatform());
                for (int j = 0; j < result.size(); j ++) {
                    QueryRecord row = result.get(j);
                    QueryRecord previousRow = j > 0 ? result.get(j - 1) : null;

                    if (row.getAsLong("game_id") != match.getGameId()) continue;

                    TierDivisionType rank = row.getAsTier("rank");
                    TierDivisionType prevRank = previousRow != null ? row.getAsTier("rank") : null;

                    String displayRank = LeagueMessageUtils.getFormattedRank(rank, true);

                    String gain = row.getAsInt("gain") > 0 ? "+" + row.getAsInt("gain") + " LP" : row.getAsInt("gain") + "";
                    if (prevRank != null) {
                        lpLabel = LeagueMessageUtils.getFormattedRank(prevRank, true) + " " + previousRow.getAsInt("lp") + "LP to " + displayRank + " " + row.getAsInt("lp") + "LP (" + gain + ")";
                    }


                    if (rank == TierDivisionType.UNRANKED) {
                        lpLabel = "Placement: "  + (me.didWin() ? "WIN" : "LOSE");
                    }
                    else if (j > 0 && row.getAsInt("rank") < result.get(j - 1).getAsInt("rank")) {
                        lpLabel = "Promoted to " + displayRank + " " + row.getAsInt("lp") + "LP";
                    }
                    else if (j > 0 && row.getAsInt("rank") > result.get(j - 1).getAsInt("rank")) {
                        lpLabel = "Demoted to " + displayRank + " " + row.getAsInt("lp") + "LP";
                    }
                    else if (!row.getAsBoolean("win") && row.getAsInt("gain") == 0) {
                        lpLabel += "-0 LP";
                    }
                }

                for (MatchTeam team : match.getTeams()) {
                    if (team.getTeamId() != TeamType.BLUE && team.getTeamId() != TeamType.RED) continue;

                    String banText = teamStats.get(team.getTeamId()).getOrDefault("ban", "**Bans**\n");
                    for (ChampionBan ban : team.getBans()) {
                        if (ban.getChampionId() == -1) banText += CustomEmojiHandler.getFormattedEmoji("0") + " ";
                        else banText += CustomEmojiHandler.getFormattedEmoji(LeagueHandler.getRiotApi().getDDragonAPI().getChampion(ban.getChampionId()).getName()) + " ";

                    }
                    teamStats.get(team.getTeamId()).put("bans", banText);
                }


                for (MatchParticipant participant : match.getParticipants()) {
                    int kills = participant.getKills();
                    int tower = participant.getTurretKills();
                    int gold = participant.getGoldEarned();

                    int totalKills = Integer.valueOf(teamStats.get(participant.getTeam()).getOrDefault("kills", "0")) + kills;
                    int totalTowers = Integer.valueOf(teamStats.get(participant.getTeam()).getOrDefault("towers", "0")) + tower;
                    int totalGold = Integer.valueOf(teamStats.get(participant.getTeam()).getOrDefault("gold", "0")) + gold;

                    teamStats.get(participant.getTeam()).put("kills", String.valueOf(totalKills));
                    teamStats.get(participant.getTeam()).put("towers", String.valueOf(totalTowers));
                    teamStats.get(participant.getTeam()).put("gold", String.valueOf(totalGold));

                    String championText = teamStats.get(participant.getTeam()).getOrDefault("champions", "**Picks**\n");

                    String rank = LeagueHandler.getRankIcon(LeagueHandler.getRankEntry(participant.getPuuid(), match.getPlatform()));
                    String name = CustomEmojiHandler.getFormattedEmoji(participant.getChampionName()) + " **" + participant.getRiotIdName() + "#" + participant.getRiotIdTagline() + "**";
                    String kda = participant.getKills() + "/" + participant.getDeaths() + "/" + participant.getAssists() + "(" + (participant.getTotalMinionsKilled() + participant.getNeutralMinionsKilled()) + " CS)";

                    championText += name + rank + "\n`" + kda + "`\n";
                    teamStats.get(participant.getTeam()).put("champions", championText);

                    HashMap<String, String> stats = new HashMap<>();
                    stats.put("damageDealt", String.valueOf(participant.getTotalDamageDealtToChampions()));
                    stats.put("damageTaken", String.valueOf(participant.getTotalDamageTaken()));
                    stats.put("heal", String.valueOf(participant.getTotalHeal()));
                    stats.put("vision", String.valueOf(participant.getVisionScore()));

                    totalStats.put(participant, stats);

                }

                eb.setDescription((me.didWin() ? "Win" : "Lose") + " as " + CustomEmojiHandler.getFormattedEmoji(me.getChampionName()) + " " + me.getChampionName() + " in " + match.getGameDurationAsDuration().toMinutes() + " minutes\n" + lpLabel);

                String killsIcon = CustomEmojiHandler.getFormattedEmoji("kda");
                String goldIcon = CustomEmojiHandler.getFormattedEmoji("golds2");
                String towericon = CustomEmojiHandler.getFormattedEmoji("tower");

                blueSide += killsIcon + teamStats.get(blue).get("kills") + " ∙ " + towericon + teamStats.get(blue).get("towers") + " ∙ " + goldIcon + " " + LeagueMessageUtils.formatNumber(teamStats.get(blue).get("gold")) + "\n" + teamStats.get(blue).get("bans") + "\n\n" + teamStats.get(blue).get("champions");
                redSide += killsIcon + teamStats.get(red).get("kills") + " ∙ " + towericon + teamStats.get(red).get("towers") + " ∙ " + goldIcon + " " + LeagueMessageUtils.formatNumber(teamStats.get(red).get("gold")) + "\n" + teamStats.get(red).get("bans") + "\n\n" + teamStats.get(red).get("champions");
                eb.addField("Blue Side", blueSide, true);
                eb.addField("Red Side", redSide, true);

                personalstats = totalStats.get(me);


                totalCreeps = me.getTotalMinionsKilled() + me.getNeutralMinionsKilled();
                totalKill = Double.valueOf(teamStats.get(me.getTeam()).get("kills")) == 0 ? 1 : Double.valueOf(teamStats.get(me.getTeam()).get("kills"));
                killParticipation = String.format("%.1f", (Double.valueOf(me.getKills()) + Double.valueOf(me.getAssists())) / totalKill * 100);
                csPerMin = String.format("%.1f", totalCreeps / Double.valueOf(match.getGameDurationAsDuration().toMinutes()));

                personalStatsTxt = "**KDA**: " + me.getKills() + "/" + me.getDeaths() + "/" + me.getAssists() + " (" +  killParticipation + "% kill participation)\n"
                                        + "**CS**: " + totalCreeps + " (" + csPerMin + " CS/min)\n"
                                        + "**Vision Score**: " + me.getVisionScore() + " (" + me.getWardsPlaced() + " wards placed)\n"
                                        + "**Damage Dealt to champion**: " + LeagueMessageUtils.formatNumber(personalstats.get("damageDealt")) + " (" + LeagueMessageUtils.getPosition(totalStats, personalstats, "damageDealt") + "th in the game)\n";

                eb.addField("Personal Stats", personalStatsTxt, false);

                build = CustomEmojiHandler.getFormattedEmoji( String.valueOf(me.getSummoner1Id()) + "_") + LeagueMessageUtils.getFormattedRunes(me, 0) + "\n"
                        + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getSummoner2Id()) + "_") + LeagueMessageUtils.getFormattedRunes(me, 1) + "\n"
                        + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem0())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem1())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem2())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem3())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem4())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem5())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem6()));
                eb.addField("Build", build, false);

                break;
        }
        return eb;
    }

    public static List<String> getMatchIds(Summoner s, GameQueueType queue, int index) {
        List<String> gameIds = new ArrayList<>();
        List<String> allIds = LeagueService.getMatchList(s, queue, index);

        for (String gameId : allIds) {
            if (gameId.split("_")[0].equalsIgnoreCase(s.getPlatform().toString()))
                gameIds.add(gameId);
        }

        if (gameIds.size() > 5) return gameIds;

        for (String gameId : allIds)
            if (!gameIds.contains(gameId)) gameIds.add(gameId);

        return gameIds;
    }

    private static EmbedBuilder getOpggEmbedMatch(EmbedBuilder eb, LOLMatch match, Summoner s, QueryResult result) {
        MatchParticipant me = null;
        for(MatchParticipant mp : match.getParticipants()){
            if(mp.getPuuid().equals(s.getPUUID())){
                me = mp;
            }
        }
        ArrayList<String> blue = new ArrayList<>();
        ArrayList<String> red = new ArrayList<>();
        for(MatchParticipant searchMe : match.getParticipants()){
            String participantString = CustomEmojiHandler.getFormattedEmoji(searchMe.getChampionName())
                                        + " "
                                        + searchMe.getKills() + "/" + searchMe.getDeaths() + "/" + searchMe.getAssists();

            if(searchMe.getTeam() == TeamType.BLUE)
                blue.add(participantString);
            else
                red.add(participantString);
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
            + date  + " | ** " + LeagueMessageUtils.getFormattedDuration((match.getGameDuration())) + "**\n"
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
                + date + " | **"+ LeagueMessageUtils.getFormattedDuration((match.getGameDuration()))  + "**\n"
                + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getSummoner1Id()) + "_") + CustomEmojiHandler.getFormattedEmoji("a" + String.valueOf(me.getPlayerAugment1())) + " " + CustomEmojiHandler.getFormattedEmoji("a" + String.valueOf(me.getPlayerAugment2())) + "\n"
                + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getSummoner2Id()) + "_") + CustomEmojiHandler.getFormattedEmoji("a" + String.valueOf(me.getPlayerAugment3())) + " " + CustomEmojiHandler.getFormattedEmoji("a" + String.valueOf(me.getPlayerAugment4())) + "\n"
                + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem0())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem1())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem2())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem3())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem4())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem5()));


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

                boolean is3v3 = prova.get("teamporos").size() == 3;


                int spacing = is3v3 ? 3 : 2;
                int teamCount = is3v3 ? 3 : 4;
                

                String blueTeam = "";
                String redTeam = "";
                for (int j = 1; j <= prova.keySet().size(); j++) {
                    String team = positions.get(j);
                    if (!prova.containsKey(team)) continue;

                    String space = is3v3 ? "\n" : (j % 2 == 0 ? "\n\n" : "\n");
                    String champs = prova.get(team).subList(0, spacing).stream().collect(Collectors.joining(""));
                    if (j <= teamCount)
                        blueTeam += CustomEmojiHandler.getFormattedEmoji(team) + champs + space;
                    else
                        redTeam += CustomEmojiHandler.getFormattedEmoji(team) + champs + space; 
                }
                String cherryTitle = is3v3 ? "ARENA 3v3" : "ARENA";
                eb.addField(
                    cherryTitle + ": " + (me.didWin() ? "WIN" : "LOSE") , content, true);

                eb.addField(is3v3 ? "Top 3" : "Top 4", blueTeam, true);
                eb.addField("Others", redTeam, true);
            break;

            default:
            String matchTitle = GameQueueTypeUtils.prettyName(match.getQueue()) + ": " + (me.didWin() ? "WIN" : "LOSE");
            for (int j = 0; j < result.size(); j ++) {
                QueryRecord row = result.get(j);
                if (row.getAsLong("game_id") != match.getGameId()) continue;

                TierDivisionType rank = row.getAsTier("rank");

                String displayRank = LeagueMessageUtils.getFormattedRank(rank, true);

                String gain = row.getAsInt("gain") > 0 ? "+" + row.getAsInt("gain") + " LP" : row.getAsInt("gain") + " LP";


                if (rank == TierDivisionType.UNRANKED) {
                    matchTitle = "Placement: "  + (me.didWin() ? "WIN" : "LOSE");
                }
                else if (j > 0 && row.getAsTier("rank").ordinal() < result.get(j - 1).getAsTier("rank").ordinal()) {
                    matchTitle = "Promoted to " + displayRank + " " + row.getAsInt("lp") + "LP";
                }
                else if (j > 0 && row.getAsTier("rank").ordinal() > result.get(j - 1).getAsTier("rank").ordinal()) {
                    matchTitle = "Demoted to " + displayRank + " " + row.getAsInt("lp") + "LP";
                }
                else if (!row.getAsBoolean("win") && row.getAsInt("gain") == 0) {
                    matchTitle += "-0 LP";
                }
                else {
                    matchTitle += " " + gain;
                }
            }
            content = CustomEmojiHandler.getFormattedEmoji(me.getChampionName()) + kda + " | " + "**Vision: **"+ me.getVisionScore()+"\n"
                        + date  + " | ** " + LeagueMessageUtils.getFormattedDuration((match.getGameDuration())) + "**\n"
                        + CustomEmojiHandler.getFormattedEmoji( String.valueOf(me.getSummoner1Id()) + "_") + LeagueMessageUtils.getFormattedRunes(me, 0) + "\n"
                        + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getSummoner2Id()) + "_") + LeagueMessageUtils.getFormattedRunes(me, 1) + "\n"
                        + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getRoleBoundItem())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem6())) + " | " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem0())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem1())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem2())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem3())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem4())) + " " + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.getItem5()));
                        eb.addField(
                            matchTitle, content, true);
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
        return eb;
    }

    private static EmbedBuilder getOpggEmbedMatch(EmbedBuilder eb, Match match, Summoner summoner) {
        Participant me = null;
        for(Participant mp : match.participants){
            if(mp.puuid.equals(summoner.getPUUID())){
                me = mp;
                break;
            }
        }

        ArrayList<String> blue = new ArrayList<>();
        ArrayList<String> red = new ArrayList<>();
        for(Participant searchMe : match.participants) {
            String participantString = CustomEmojiHandler.getFormattedEmoji(ChampionUtils.getChampion(searchMe.champion).getName())
                                        + " "
                                        + searchMe.kda;

            if(searchMe.team == TeamType.BLUE)
                blue.add(participantString);
            else
                red.add(participantString);
        }


        String kda = me.kda;
        String content = "";
        Instant instant = Instant.ofEpochMilli(match.timeStart + match.getDuration() + 3600000*2);
        ZoneOffset offset = ZoneOffset.UTC;
        OffsetDateTime offsetDateTime = instant.atOffset(offset);
        String date = DateHandler.formatDate(offsetDateTime);
        date = "<t:" + ((match.timeStart/1000) + (match.getDuration()/1000)) + ":R>";
        switch (match.queue){
            case STRAWBERRY:
                content = CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.champion)) + " Level: " + 
                        (me.skillOrder.size() > 0 ? me.skillOrder.size() : 1) + 
                        " | " + CustomEmojiHandler.getFormattedEmoji("golds") + me.goldEarned + "\n"
                        + date + " | ** " + LeagueMessageUtils.getFormattedDuration(match.getDuration()) + "**\n"
                        + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.item0)) + " " 
                        + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.item1)) + " " 
                        + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.item2)) + " " 
                        + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.item3)) + " " 
                        + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.item4)) + " " 
                        + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.item5)) + " " 
                        + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.item6));
                
                eb.addField("Swarm" + ": " + (me.win ? "WIN" : "LOSE"), content, true);

                String swarmTeam = "";
                for(Participant mt : match.participants)
                    swarmTeam += CustomEmojiHandler.getFormattedEmoji(String.valueOf(mt.champion)) + " Level: " +  
                            (mt.skillOrder.size() > 0 ? mt.skillOrder.size() : 1) + 
                            " | " + CustomEmojiHandler.getFormattedEmoji("golds") + mt.goldEarned + "\n";

                eb.addField("Swarm Team", swarmTeam, true);
                eb.addBlankField(true);
                break;

            case CHERRY:
                content = CustomEmojiHandler.getFormattedEmoji(ChampionUtils.getChampion(me.champion).getName()) + kda + "\n"
                        + date + " | **" + LeagueMessageUtils.getFormattedDuration(match.getDuration()) + "**\n"
                        + CustomEmojiHandler.getFormattedEmoji(me.summonerSpell1 + "_")
                        + CustomEmojiHandler.getFormattedEmoji("a" + (me.augments.size() > 0 ? me.augments.get(0) : "0")) + " " 
                        + CustomEmojiHandler.getFormattedEmoji("a" + (me.augments.size() > 1 ? me.augments.get(1) : "0")) + "\n"
                        + CustomEmojiHandler.getFormattedEmoji(me.summonerSpell2 + "_")
                        + CustomEmojiHandler.getFormattedEmoji("a" + (me.augments.size() > 2 ? me.augments.get(2) : "0")) + " " 
                        + CustomEmojiHandler.getFormattedEmoji("a" + (me.augments.size() > 3 ? me.augments.get(3) : "0")) + "\n"
                        + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.item0)) + " " 
                        + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.item1)) + " " 
                        + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.item2)) + " " 
                        + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.item3)) + " " 
                        + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.item4)) + " " 
                        + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.item5));

                eb.addField("ARENA: " + (me.win ? "WIN" : "LOSE"), content, true);

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

                for(Participant mt : match.participants){
                    String name = "";
                    String team = "";
                    switch (mt.subTeam) {
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
                    prova.get(team).add(CustomEmojiHandler.getFormattedEmoji(ChampionUtils.getChampion(mt.champion).getName()) + name);
                    positions.put(mt.subTeamPlacement, team);
                }
                
                String blueTeam = "";
                String redTeam = "";
                for (int j = 1; j <= 8; j++) {
                    String team = positions.get(j);
                    if (team != null && prova.get(team) != null && prova.get(team).size() >= 2) {
                        String space = j % 2 == 0 ? "\n\n" : "\n";
                        if (j <= 4)
                            blueTeam += CustomEmojiHandler.getFormattedEmoji(team) + prova.get(team).get(0) + prova.get(team).get(1) + space;
                        else
                            redTeam += CustomEmojiHandler.getFormattedEmoji(team) + prova.get(team).get(0) + prova.get(team).get(1) + space;
                    }
                }
                eb.addField("Top 4", blueTeam, true);
                eb.addField("Others", redTeam, true);
                break;

            default:
                String matchTitle = GameQueueTypeUtils.prettyName(match.queue) + ": " + (me.win ? "WIN" : "LOSE");
                content = CustomEmojiHandler.getFormattedEmoji(ChampionUtils.getChampion(me.champion).getName()) + kda + " | " + "**Vision: **" + me.visionScore + "\n"
                        + date + " | ** " + LeagueMessageUtils.getFormattedDuration(match.getDuration()) + "**\n"
                        + CustomEmojiHandler.getFormattedEmoji(me.summonerSpell1 + "_") 
                        + getFormattedPrimaryRunes(me) + "\n"
                        + CustomEmojiHandler.getFormattedEmoji(me.summonerSpell2 + "_") 
                        + getFormattedSecondaryRunes(me) + "\n"
                        + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.item0)) + " " 
                        + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.item1)) + " " 
                        + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.item2)) + " " 
                        + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.item3)) + " " 
                        + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.item4)) + " " 
                        + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.item5)) + " " 
                        + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.item6));
                
                eb.addField(matchTitle, content, true);
                String blueS = "";
                String redS = "";
                for(int j = 0; j < 5 && j < blue.size(); j++)
                    blueS += blue.get(j) + "\n";
                for(int j = 0; j < 5 && j < red.size(); j++)
                    redS += red.get(j) + "\n";
                eb.addField("Blue Side", blueS, true);
                eb.addField("Red Side", redS, true);
                break;
        }
        return eb;
    }

    private static String getFormattedPrimaryRunes(Participant p) {
        StringBuilder sb = new StringBuilder();
        sb.append(CustomEmojiHandler.getFormattedEmoji(LeagueHandler.getFatherRuneById(p.primaryRunes.get(0))));
        for (int i = 1; i < p.primaryRunes.size(); i++) {
            sb.append(CustomEmojiHandler.getFormattedEmoji(String.valueOf(p.primaryRunes.get(i))));
            if (i < 1) sb.append(" ");
        }
        return sb.toString();
    }

    private static String getFormattedSecondaryRunes(Participant p) {
        StringBuilder sb = new StringBuilder();
        sb.append(CustomEmojiHandler.getFormattedEmoji(LeagueHandler.getFatherRuneById(p.secondaryRunes.get(0))));
        for (int i = 1; i < p.secondaryRunes.size(); i++) {
            sb.append(CustomEmojiHandler.getFormattedEmoji(String.valueOf(p.secondaryRunes.get(i))));
            if (i < 1) sb.append(" ");
        }
        return sb.toString();
    }

    public static EmbedBuilder getOpggEmbed(Summoner s, LeagueMessageParameter parameter) {
        List<LOLMatch> matches = loadMatchesParallel(s, parameter.getQueueType(), parameter.getOffset());
        return getOpggEmbed(s, parameter, matches);
    }

    public static EmbedBuilder getOpggEmbed(Summoner s, LeagueMessageParameter parameter, List<LOLMatch> matches) {
        LeagueShard shard = s.getPlatform();

        RiotAccount account = LeagueService.getRiotAccountFromSummoner(s);
        EmbedBuilder eb = new EmbedBuilder();

        eb.setAuthor(account.getName() + "#" + account.getTag(), null, LeagueHandler.getSummonerProfilePic(s));
        eb.setColor(Bot.getColor());
        eb.setTitle("Showing matches from " + LeagueShardUtils.getRegionFlag(shard) + " " + shard.getRealmValue());

        ChronoTask task = (() -> {
            LeagueDB.addLOLAccount(s);
        });
        task.queue();

        QueryResult result = LeagueService.getSummonerData(s.getPUUID(), s.getPlatform());

        for (LOLMatch match : matches) {
            try {
                if (Tracker.isRemake(match)) continue;
                Tracker.queueMatch(match);
                if (match.getParticipants().size() == 0) continue;

                ChronoTask MatchTask = (() -> {
                    LeagueHandler.updateSummonerDB(match);
                });
                MatchTask.queue();
                eb = getOpggEmbedMatch(eb, match, s, result);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (eb.getFields().size() == 0)
            eb.setDescription("No games found");

        return eb;
    }

    public static List<MessageTopLevelComponent> getOpggButtons(Summoner s, String user_id, LeagueMessageParameter parameter) {
        List<LOLMatch> matches = loadMatchesParallel(s, parameter.getQueueType(), parameter.getOffset());
        return getOpggButtons(s, user_id, parameter, matches);
    }

    public static List<MessageTopLevelComponent> getOpggButtons(Summoner s, String user_id, LeagueMessageParameter parameter, List<LOLMatch> matches) {
        int index = parameter.getOffset();
        GameQueueType queue = parameter.getQueueType();
        int order = 0;
        Button left = Button.primary(BUTTON_ID_PREFIX + "-leftpage-" + index, " ").withEmoji(CustomEmojiHandler.getRichEmoji("leftarrow"));
        if (index == 0) left = left.asDisabled();

        Button page = Button.primary(BUTTON_ID_PREFIX + "-index-" + index, "Match " + ((index/5)+1)).asDisabled();
        Button right = Button.primary(BUTTON_ID_PREFIX + "-rightpage-" + index, " ").withEmoji(CustomEmojiHandler.getRichEmoji("rightarrow"));

        List<MessageTopLevelComponent> buttons = new ArrayList<>(composeButtons(s, user_id, parameter));

        StringSelectMenu menu = LeagueMessage.getOpggMenu(s, matches, parameter);
        if (menu != null) {
            buttons.add(0, ActionRow.of(menu));
            order++;
        }
        if (parameter.getMatch() != null) {
            StringSelectMenu matchMenu = getSelectedMatchMenu(parameter.getMatch());
            buttons.add(1, ActionRow.of(matchMenu));
        } else {
            buttons.add(order, LeagueMessageUtils.getOpggQueueTypeButtons(queue));
        }
        order++;
        buttons.add(order, ActionRow.of(left, page, right));

        return buttons;
    }

    public static StringSelectMenu getSelectedMatchMenu(LOLMatch match) {
        ArrayList<SelectOption> options = new ArrayList<>();
        for(MatchParticipant p : match.getParticipants()){
            Emoji icon = ChampionUtils.getEmojiByChampion(p.getChampionId());
            options.add(SelectOption.of(p.getRiotIdName() + "#" + p.getRiotIdTagline(), p.getPuuid() + "#" + match.getPlatform().name()).withEmoji(icon));
        }

        return StringSelectMenu.create(LeagueMessage.BUTTON_ID_PREFIX + "-rankselect")
                .setPlaceholder("Select a summoner")
                .setMaxValues(1)
                .addOptions(options)
                .build();
    }

//   ▄█        ▄█   ▄█    █▄     ▄████████
//  ███       ███  ███    ███   ███    ███
//  ███       ███▌ ███    ███   ███    █▀
//  ███       ███▌ ███    ███  ▄███▄▄▄
//  ███       ███▌ ███    ███ ▀▀███▀▀▀
//  ███       ███  ███    ███   ███    █▄
//  ███▌    ▄ ███  ███    ███   ███    ███
//  █████▄▄██ █▀    ▀██████▀    ██████████
//  ▀

    public static EmbedBuilder getLivegameEmbed(Summoner summoner, SpectatorGameInfo game, List<SpectatorParticipant> spectators) {
        RiotAccount account = LeagueService.getRiotAccountFromSummoner(summoner);
        if (game == null || spectators == null || spectators.isEmpty()) {
            EmbedBuilder empty = new EmbedBuilder();
            empty.setTitle(account.getName() + "'s Game");
            empty.setColor(Bot.getColor());
            empty.setThumbnail(LeagueHandler.getSummonerProfilePic(summoner));
            empty.setDescription("This user is not in a game.");
            return empty;
        }
        try {
            EmbedBuilder builder = new EmbedBuilder();
            builder.setAuthor(account.getName() + "#" + account.getTag(), null, LeagueHandler.getSummonerProfilePic(summoner));
            builder.setDescription("Currently playing a **" + GameQueueTypeUtils.prettyName(game.getGameQueueConfig()) + "** started <t:" + ((game.getGameStart() / 1000)) + ":R>");
            builder.setColor(Bot.getColor());
            builder.setThumbnail(LeagueHandler.getSummonerProfilePic(summoner));

            switch (game.getGameQueueConfig()) {
                case CHERRY:
                    String field1 = "";
                    String field2 = "";
                    int i = 0;

                    for (SpectatorParticipant participant : spectators) {
                        Summoner s = LeagueService.getSummonerByPuuid(participant.getPuuid(), summoner.getPlatform());
                        String mastery = LeagueHandler.getMasteryByChamp(s, participant.getChampionId());
                        String stats = LeagueHandler.getRankIcon(LeagueHandler.getRankEntry(s));
                        String sum = " **" + participant.getRiotId() + "**";

                        if (i < 8) field1 += mastery + " " + sum + " " + stats + "\n";
                        else if (i < 16) field2 += mastery + " " + sum + " " + stats + "\n";
                        i++;
                    }
                    builder.addField("1 - 8 Players", field1, false);
                    builder.addField("8 - 16 Players", field2, false);
                    break;

                default:
                    String blueSide = "", redSide = "";
                    String blueBans = "", redBans = "";
                    String entryName = "";

                    if (game.getBannedChampions() != null) {
                        for (BannedChampion bc : game.getBannedChampions()) {
                            String bcIcon = ChampionUtils.getFormattedEmojiByChampion(bc.getChampionId());

                            if (bc.getTeamId() == TeamType.BLUE.getValue()) blueBans += bcIcon + " ";
                            else redBans += bcIcon + " ";
                        }
                    }

                    for (SpectatorParticipant participant : spectators) {
                        String championIcon = ChampionUtils.getFormattedEmojiByChampion(participant.getChampionId());

                        String stats = CustomEmojiHandler.getFormattedEmoji("unranked") + "\n`Unranked`";
                        LeagueEntry entry = LeagueHandler.getEntry(game.getGameQueueConfig(), participant.getPuuid(), summoner.getPlatform());
                        if (entry != null) {
                            int wins = entry.getWins();
                            int losses = entry.getLosses();
                            double winrate = (Double.valueOf(wins) / Double.valueOf(wins + losses)) * 100;
                            stats = CustomEmojiHandler.getFormattedEmoji(entry.getTier()) + "\n`" + LeagueMessageUtils.getFormattedRank(entry.getTierDivisionType(), false) + " " + String.valueOf(entry.getLeaguePoints()) + "LP \n" + wins + "W/" + losses + "L " + "(" + Math.ceil(winrate) + " WR%)`";
                            entryName = GameQueueTypeUtils.prettyName(entry.getQueueType());
                        }

                        String field = championIcon + "**" + participant.getRiotId() + "**" + stats + "\n";

                        if (participant.getTeam() == TeamType.BLUE) blueSide += field;
                        else redSide += field;

                    }

                    builder.addField("Rank queue", "Showing ranks about " + entryName, false);

                    builder.addField("**BLUE SIDE**", "**Bans\n**" + blueBans + "\n\n**Picks**\n" + blueSide, true);
                    builder.addField("**RED SIDE**", "**Bans\n**" + redBans + "\n\n**Picks**\n" + redSide, true);
                    break;
            }

            LeagueHandler.updateSummonerDB(game);


            builder.setFooter("For every gamemode would be use the SoloQ ranked data. Flex would be shown only if the game is a Flex game.");
            return builder;

        } catch (Exception e) {
            EmbedBuilder builder = new EmbedBuilder();
            builder.setTitle(account.getName() + "'s Game");
            builder.setColor(Bot.getColor());
            builder.setThumbnail(LeagueHandler.getSummonerProfilePic(summoner));
            builder.setDescription("This user is not in a game.");
            return builder;
        }
    }

    public static StringSelectMenu getLivegameMenu(Summoner summoner, List<SpectatorParticipant> spectators) {
        if (spectators == null || spectators.size() == 0) return null;

        ArrayList<SelectOption> options = new ArrayList<>();
        for(SpectatorParticipant p : spectators){
            if (p.getPuuid() == null) continue;
            Emoji icon = ChampionUtils.getEmojiByChampion(p.getChampionId());
            options.add(SelectOption.of(p.getRiotId(), p.getPuuid() + "#" + summoner.getPlatform().name()).withEmoji(icon));
        }

        return StringSelectMenu.create("rank-select")
                .setPlaceholder("Select a summoner")
                .setMaxValues(1)
                .addOptions(options)
                .build();
    }

    private static MessageEmbed buildEmbedChampion(String userId, Summoner summoner, String puuid, LeagueMessageParameter parameter) {
        RiotAccount account = LeagueService.getRiotAccountFromSummoner(summoner);
        List<Match> matches = null;
        matches = MongoDB.getMatchHistory(puuid, parameter);
        
        EmbedBuilder eb = new EmbedBuilder();
        if (parameter.isShowChampion()) eb.setThumbnail(ChampionUtils.getChampionProfilePic(parameter.getChampion().getId()));
        else eb.setThumbnail(LeagueHandler.getSummonerProfilePic(summoner));

        eb.setAuthor(account.getName() + "#" + account.getTag(), null, LeagueHandler.getSummonerProfilePic(summoner));
        eb.setColor(Bot.getColor());
        switch (parameter.getMessageType()) {
            case OVERVIEW:
                eb = getGenericStats(eb, matches, summoner, puuid, parameter);
                break;
            case MATCHUP:
                eb = getMatchups(eb, matches, puuid, parameter);
                break;
            case OVERVIEW_PING:
                eb = getPings(eb, matches, puuid);
                break;
            case OVERVIEW_OBJECTIVES:
                eb = getObjectives(eb, matches, summoner, puuid);
                break;
            case OVERVIEW_CHAMPIONS:
                eb = getAllChampions(eb, matches, summoner, puuid, parameter);
                break;
            case OVERVIEW_OPGG:
                eb = getChampionOPGG(eb, matches, summoner, puuid, parameter);
                break;
            default:
                break;
        }
        return eb.build();
    }

    private static List<MessageTopLevelComponent> getChampionButtons(String userId, Summoner summoner, LeagueMessageParameter parameter) {
        StaticChampion champion = parameter.getChampion();
        
        Button left = Button.primary("lol-left", " ").withEmoji(CustomEmojiHandler.getRichEmoji("leftarrow"));
        Button right = Button.primary("lol-right", " ").withEmoji(CustomEmojiHandler.getRichEmoji("rightarrow"));

        RiotAccount account = LeagueService.getRiotAccountFromSummoner(summoner);
        Button center = Button.primary("lol-center-" + summoner.getPUUID() + "#" + summoner.getPlatform().name(), account.getName());
        center = center.asDisabled();

        Button settings = Button.primary("lol-change-" + parameter.getChampionId(), " ").withEmoji(CustomEmojiHandler.getRichEmoji("shuffle"));


        Button championButton = Button.secondary("lol-champion-0", " ").withEmoji(CustomEmojiHandler.getRichEmoji("blank")).asDisabled();
        if (champion != null) {
            championButton = Button.secondary("lol-champion-" + champion.getId(), champion.getName()).withEmoji(CustomEmojiHandler.getRichEmoji(champion.getName()));
            championButton = parameter.isShowChampion() ? championButton.withStyle(ButtonStyle.SUCCESS) : championButton;
        }

        Button generic = Button.primary("lol-type-" + LeagueMessageType.OVERVIEW, "Overview");
        Button profile = Button.primary("lol-type-" + LeagueMessageType.PROFILE, " ").withEmoji(CustomEmojiHandler.getRichEmoji("leftarrow"));
        Button matchups = Button.primary("lol-type-" + LeagueMessageType.MATCHUP, "Matchups");
        Button pings = Button.primary("lol-type-" + LeagueMessageType.OVERVIEW_PING, "Pings");
        Button objectives = Button.primary("lol-type-" + LeagueMessageType.OVERVIEW_OBJECTIVES, "Objectives");
        Button champions = Button.primary("lol-type-" + LeagueMessageType.OVERVIEW_CHAMPIONS, "Champions");
        Button opgg = Button.primary("lol-type-" + LeagueMessageType.OVERVIEW_OPGG, "Opgg");

        switch (parameter.getMessageType()) {
            case OVERVIEW:
                generic = generic.withStyle(ButtonStyle.SUCCESS).asDisabled();
                break;
            case MATCHUP:
                matchups = matchups.withStyle(ButtonStyle.SUCCESS).asDisabled();
                break;
            case OVERVIEW_PING:
                pings = pings.withStyle(ButtonStyle.SUCCESS).asDisabled();
                break;
            case OVERVIEW_OBJECTIVES:
                objectives = objectives.withStyle(ButtonStyle.SUCCESS).asDisabled();
                break;
            case OVERVIEW_CHAMPIONS:
                champions = champions.withStyle(ButtonStyle.SUCCESS).asDisabled();
                break;
            case OVERVIEW_OPGG:
                opgg = opgg.withStyle(ButtonStyle.SUCCESS).asDisabled();
                break;
            case PROFILE:
                profile = profile.withStyle(ButtonStyle.SUCCESS).asDisabled();
                break;
            default:
                break;
        }

        Button allSeason = Button.secondary("lol-season-all", "General");
        Button currentSplit = Button.secondary("lol-season-current", "Current Split");
        Button previousSplit = Button.secondary("lol-season-previous", "Previous Split");

        long[] time = SeasonUtils.getCurrentSplitRange();
        long[] previousTime = SeasonUtils.getPreviousSplitRange();

        if (parameter.getTimeStart() == 0) allSeason = allSeason.withStyle(ButtonStyle.SUCCESS);
        else if (parameter.getTimeStart() == time[0]) currentSplit = currentSplit.withStyle(ButtonStyle.SUCCESS);
        else if (parameter.getTimeStart() == previousTime[0] && parameter.getTimeEnd() == previousTime[1]) previousSplit = previousSplit.withStyle(ButtonStyle.SUCCESS);

        List<MessageTopLevelComponent> rows = new ArrayList<>();
        if (parameter.getQueueType() == null || GameQueueTypeUtils.hasLane(parameter.getQueueType())) rows.add(LeagueMessageUtils.getLaneComponents(parameter.getLaneType()));

        rows.add(LeagueMessageUtils.getOpggQueueTypeButtons(ButtonStyle.SECONDARY, parameter.getQueueType()));
        rows.add(ActionRow.of(allSeason, currentSplit, previousSplit));
        rows.add(ActionRow.of(profile, generic, opgg, champions, matchups));


        if (parameter.getMessageType().hasPageButtons()) {
            Button leftPage = Button.secondary("lol-leftpage-" + parameter.getOffset(), "Previous Page");
            Button rightPage = Button.secondary("lol-rightpage-" + parameter.getOffset(), "Next Page");

            if (parameter.getOffset() == 0) 
                leftPage = leftPage.asDisabled();
            
            if (parameter.getMessageType() == LeagueMessageType.OVERVIEW_OPGG) {
                if (userId != null && LeagueHandler.getNumberOfProfile(userId) > 1)
                    center = center.withStyle(ButtonStyle.SUCCESS).asEnabled();
                rows.add(ActionRow.of(center, championButton, settings, leftPage, rightPage));
            }
            else {
                if (userId != null && LeagueHandler.getNumberOfProfile(userId) > 1)
                    rows.add(ActionRow.of(left, center, right, leftPage, rightPage));
                else 
                    rows.add(ActionRow.of(center, leftPage, rightPage));
            }
            return rows;
        }

        if (userId != null && LeagueHandler.getNumberOfProfile(userId) > 1)
            rows.add(ActionRow.of(left, center, right, championButton, settings));
        else 
            rows.add(ActionRow.of(center, championButton, settings));

        return rows;
    }

    private static EmbedBuilder getPings(EmbedBuilder eb, List<Match> matches, String puuid) {
        HashMap<String, Integer> pings = new HashMap<>();

        for (Match match : matches) {
            for (Participant participant : match.participants) {
                if (!puuid.equals(participant.puuid)) continue;
                for (String ping : participant.pings.keySet()) 
                    pings.put(ping, pings.getOrDefault(ping, 0) + participant.pings.get(ping));
            }
        }

        List<Map.Entry<String, Integer>> sortedPings = pings.entrySet()
            .stream()
            .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
            .collect(Collectors.toList());

        String pingString = "";
        for (Map.Entry<String, Integer> entry : sortedPings) {
            if (entry.getKey().equals("basic")) continue;

            String pingName = "";
            switch (entry.getKey()) {
                case "command":
                    pingName = "Generic Ping";
                    break;
                default:
                    pingName = entry.getKey();
                    pingName = Arrays.stream(pingName.replace("_", " ").split(" "))
                        .map(word -> word.isEmpty() ? "" : Character.toUpperCase(word.charAt(0)) + word.substring(1).toLowerCase())
                        .collect(Collectors.joining(" "));
                    break;
            }
            pingString += CustomEmojiHandler.getFormattedEmoji(entry.getKey() + "_ping") + " " + pingName + "\n`" +
                entry.getValue() + " total (" + String.format("%.2f", (double)entry.getValue() / matches.size()) + " avg)`\n";
        }

        eb.addField("Pings Usage", pingString, false);

        return eb;
    }

    private static EmbedBuilder getMatchups(EmbedBuilder eb, List<Match> matches, String puuid, LeagueMessageParameter parameter) {
        HashMap<Integer, int[]> laneVsWinrate = new HashMap<>();
        HashMap<Integer, int[]> duoWinrate = new HashMap<>();

        HashMap<String, Set<Integer>> unique = new HashMap<>();
        unique.put("champion", new HashSet<>());

        for (Match match : matches) {
            for (Participant participant : match.participants) {
                if (!puuid.equals(participant.puuid)) {
                    continue;
                }

                LaneType lane = participant.lane;
                TeamType team = participant.team;

                boolean win = participant.win;

                List<Integer> enemyChamps = match.participants.stream()
                    .filter(p -> p.team != team)
                    .filter(p -> p.lane == lane)
                    .map(p -> p.champion)
                    .collect(Collectors.toList());

                for (int c : enemyChamps) {
                    laneVsWinrate.computeIfAbsent(c, k -> new int[2]);
                    laneVsWinrate.get(c)[(win ? 0 : 1)]++;   
                }
                             
                if (parameter.isDuo()) {
                    List<Integer> allyChamps = match.participants.stream()
                        .filter(p -> p.team == team)
                        .filter(p -> p.id != participant.id)
                        .filter(p -> (GameQueueTypeUtils.isCherry(parameter.getQueueType()) && p.subTeam == participant.subTeam) || p.lane == LaneType.BOT || p.lane == LaneType.UTILITY)
                        .map(p -> p.champion)
                        .collect(Collectors.toList());
                    
                    for (int c : allyChamps) {
                        duoWinrate.computeIfAbsent(c, k -> new int[2]);
                        duoWinrate.get(c)[(win ? 0 : 1)]++;   
                    }
                }
            }
        }

        int champs = unique.get("champion").size();
        eb.setDescription(
            "Summoner has played **" + matches.size() + "** games with " + champs + " different champions"
        );

        eb = LeagueMessageUtils.buildMatchups("matchups", eb, laneVsWinrate);
        if (parameter.isDuo())
            eb = LeagueMessageUtils.buildMatchups("duo", eb, duoWinrate);

        return eb;
    }

    private static EmbedBuilder getGenericStats(EmbedBuilder eb, List<Match> matches, Summoner summoner, String puuid, LeagueMessageParameter parameter) {

        if (matches.size() == 0) {
            eb.setDescription("Not enough games");
            return eb;
        }

        boolean isArena = GameQueueTypeUtils.isCherry(parameter.getQueueType());

        LinkedHashMap<LaneType, String> laneStats = new LinkedHashMap<>();
        LinkedHashMap<GameQueueType, String> queueStats = new LinkedHashMap<>();
        HashMap<String, Accumulator> overallStats = new HashMap<>();
        HashMap<String, Integer> pings = new HashMap<>();

        HashMap<Integer, Integer> dSpells = new HashMap<>();
        HashMap<Integer, Integer> fSpells = new HashMap<>();

        HashMap<Integer, int[]> laneVsWinrate = new HashMap<>();
        HashMap<Integer, int[]> duoWinrate = new HashMap<>();

        HashMap<String, Set<Integer>> unique = new HashMap<>();

        HashMap<Integer, PlayerChampionStats> championStats = new HashMap<>();
        HashMap<Integer, ChampionMastery> masteries = LeagueHandler.getMastery(summoner);

        long timePlayed = 0;
        long oldest = Long.MAX_VALUE;
        long newest = Long.MIN_VALUE;

        unique.put("champion", new HashSet<>());
        unique.put("lane", new HashSet<>());
        unique.put("queue", new HashSet<>());
        for (Match match : matches) {
            if (match.getDuration() <= 330) continue;
            timePlayed += match.getDuration();
            if (oldest > match.timeStart) oldest = match.timeStart;
            if (newest < match.timeStart) newest = match.timeStart;

            for (Participant participant : match.participants) {
                if (!puuid.equals(participant.puuid)) continue;

                for (String ping : participant.pings.keySet()) 
                    pings.put(ping, pings.getOrDefault(ping, 0) + participant.pings.get(ping));

                unique.getOrDefault("champion", new HashSet<>()).add(participant.champion);
                unique.getOrDefault("lane", new HashSet<>()).add(participant.lane.ordinal());
                unique.getOrDefault("queue", new HashSet<>()).add(match.queue.ordinal());

                LaneType lane = participant.lane;
                TeamType team = participant.team;
                GameQueueType gameQueue = match.queue;
                boolean win = participant.win;
    
                String kda = participant.kda;
                int kills = Integer.parseInt(kda.split("/")[0]);
                int deaths = Integer.parseInt(kda.split("/")[1]);
                int assists = Integer.parseInt(kda.split("/")[2]);
        
                laneStats.merge(lane, (win ? "1-0" : "0-1"), (oldValue, newValue) -> {
                    String[] oldStats = oldValue.split("-");
                    String[] newStats = newValue.split("-");
                    int totalWins = Integer.parseInt(oldStats[0]) + Integer.parseInt(newStats[0]);
                    int totalLosses = Integer.parseInt(oldStats[1]) + Integer.parseInt(newStats[1]);
                    return totalWins + "-" + totalLosses;
                });
    
                queueStats.merge(gameQueue, (win ? "1-0" : "0-1"), (oldValue, newValue) -> {
                    String[] oldStats = oldValue.split("-");
                    String[] newStats = newValue.split("-");
                    int totalWins = Integer.parseInt(oldStats[0]) + Integer.parseInt(newStats[0]);
                    int totalLosses = Integer.parseInt(oldStats[1]) + Integer.parseInt(newStats[1]);
                    return totalWins + "-" + totalLosses;
                });

                double min = match.getDuration() / 1000.0 / 60.0;
                double csPerMin = participant.cs / (min == 0 ? 1 : min);
    
                overallStats.computeIfAbsent("damage", k -> new Accumulator()).add(participant.damage);
                overallStats.computeIfAbsent("damage_building", k -> new Accumulator()).add(participant.damageBuilding);
                overallStats.computeIfAbsent("cs", k -> new Accumulator()).add(participant.cs);
                overallStats.computeIfAbsent("vision_score", k -> new Accumulator()).add(participant.visionScore);
                overallStats.computeIfAbsent("ward", k -> new Accumulator()).add(participant.ward);
                overallStats.computeIfAbsent("ward_killed", k -> new Accumulator()).add(participant.wardKilled);
                overallStats.computeIfAbsent("gold_earned", k -> new Accumulator()).add(participant.goldEarned);
                overallStats.computeIfAbsent("kills", k -> new Accumulator()).add(kills);
                overallStats.computeIfAbsent("deaths", k -> new Accumulator()).add(deaths);
                overallStats.computeIfAbsent("assists", k -> new Accumulator()).add(assists);
                overallStats.computeIfAbsent("cs_min", k -> new Accumulator()).add((int) csPerMin);

                overallStats.computeIfAbsent("level", k -> new Accumulator()).add(participant.level);

                overallStats.computeIfAbsent("doubles", k -> new Accumulator()).add(participant.doubles);
                overallStats.computeIfAbsent("triples", k -> new Accumulator()).add(participant.triples);
                overallStats.computeIfAbsent("quadruples", k -> new Accumulator()).add(participant.quadruples);
                overallStats.computeIfAbsent("pentas", k -> new Accumulator()).add(participant.pentas);

                overallStats.computeIfAbsent("q", k -> new Accumulator()).add(participant.q);
                overallStats.computeIfAbsent("w", k -> new Accumulator()).add(participant.w);
                overallStats.computeIfAbsent("e", k -> new Accumulator()).add(participant.e);
                overallStats.computeIfAbsent("r", k -> new Accumulator()).add(participant.r);
                overallStats.computeIfAbsent("d", k -> new Accumulator()).add(participant.d);
                overallStats.computeIfAbsent("f", k -> new Accumulator()).add(participant.f);

                dSpells.merge(LeagueMessageUtils.normalizeSpellId(participant.summonerSpell1), participant.d, Integer::sum);
                fSpells.merge(LeagueMessageUtils.normalizeSpellId(participant.summonerSpell2), participant.f, Integer::sum);

                championStats.computeIfAbsent(participant.champion, p -> new PlayerChampionStats(participant.champion)).add(kills, deaths, assists, participant.gain, participant.win);

                if (isArena) {
                    int placement = participant.subTeamPlacement;
                    if (placement == 1) overallStats.computeIfAbsent("arena_first", k -> new Accumulator()).add(1);
                    else if (placement == 2) overallStats.computeIfAbsent("arena_second", k -> new Accumulator()).add(1);
                    else if (placement == 3) overallStats.computeIfAbsent("arena_third", k -> new Accumulator()).add(1);
                    overallStats.computeIfAbsent("arena_placement", k -> new Accumulator()).add(placement);
                }

                if (match.participants.size() > 1) {
                    int teamKills;
                    int enemyTeamKills;
                    teamKills = match.participants.stream()
                        .filter(p -> {
                            if (isArena)
                                return p.subTeam == participant.subTeam;
                            return  p.team == team;
                        })
                        .mapToInt(p -> Integer.parseInt(p.kda.split("/")[0]))
                        .sum();
                    enemyTeamKills = match.participants.stream()
                        .filter(p -> {
                            if (!GameQueueTypeUtils.isCherry(parameter.getQueueType()))
                                return  p.team != team;
                            return false;
                        })
                        .mapToInt(p -> Integer.parseInt(p.kda.split("/")[0]))
                        .sum();
                    double killParticipation = teamKills == 0 ? 0 : (double) (kills + assists) / teamKills;
                    double deathShare = enemyTeamKills == 0 ? 0 : (double) deaths / enemyTeamKills;
                    overallStats.computeIfAbsent("kill_participation", k -> new Accumulator()).add((int)(killParticipation * 100));
                    overallStats.computeIfAbsent("death_share", k -> new Accumulator()).add((int)(deathShare * 100));
                }

                boolean isDuo = lane == LaneType.BOT || lane == LaneType.UTILITY;

                List<Integer> enemyChamps = match.participants.stream()
                    .filter(p -> p.team != team)
                    .filter(p -> p.lane == lane)
                    .map(p -> p.champion)
                    .collect(Collectors.toList());
                    
                for (int c : enemyChamps) {
                    laneVsWinrate.computeIfAbsent(c, k -> new int[2]);
                    laneVsWinrate.get(c)[(win ? 0 : 1)]++;   
                }

                if (isDuo) {
                    List<Integer> allyChamps = match.participants.stream()
                        .filter(p -> p.team == team)
                        .filter(p -> p.id != participant.id)
                        .filter(p -> p.lane == LaneType.BOT || p.lane == LaneType.UTILITY)
                        .map(p -> p.champion)
                        .collect(Collectors.toList());
                    
                    for (int c : allyChamps) {
                        duoWinrate.computeIfAbsent(c, k -> new int[2]);
                        duoWinrate.get(c)[(win ? 0 : 1)]++;   
                    }
                }
            }
        }

        laneStats = laneStats.entrySet()
            .stream()
            .sorted((entry1, entry2) -> {
                String[] stats1 = entry1.getValue().split("-");
                String[] stats2 = entry2.getValue().split("-");
                int totalGames1 = Integer.parseInt(stats1[0]) + Integer.parseInt(stats1[1]);
                int totalGames2 = Integer.parseInt(stats2[0]) + Integer.parseInt(stats2[1]);
                return Integer.compare(totalGames2, totalGames1);
            })
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (e1, e2) -> e1,
                LinkedHashMap::new
            ));

        queueStats = queueStats.entrySet()
            .stream()
            .sorted((entry1, entry2) -> {
                String[] stats1 = entry1.getValue().split("-");
                String[] stats2 = entry2.getValue().split("-");
                int totalGames1 = Integer.parseInt(stats1[0]) + Integer.parseInt(stats1[1]);
                int totalGames2 = Integer.parseInt(stats2[0]) + Integer.parseInt(stats2[1]);
                return Integer.compare(totalGames2, totalGames1);
            })
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (e1, e2) -> e1,
                LinkedHashMap::new
            ));

        String laneString = "";
        for (LaneType lane : laneStats.keySet()) {
            String wins = laneStats.get(lane).split("-")[0];
            String losses = laneStats.get(lane).split("-")[1];
            int games = Integer.valueOf(wins) + Integer.valueOf(losses);

            if (lane == LaneType.NONE) 
                continue;

            String percent = String.format("%.2f", Double.parseDouble(wins) * 100 / (Double.parseDouble(wins) + Double.parseDouble(losses)));
            laneString += LaneTypeUtils.getLaneTypeEmoji(lane) + " " + LaneTypeUtils.getPrettyName(lane) + " " + games + " games\n`(" +  wins + "W/" + losses + "L) - " + percent +"% WR`\n";
        }

        String gameString = "";
        int count = 0;
        int otherWins = 0;
        int otherLosses = 0;         
        for (GameQueueType game : queueStats.keySet()) {
            String wins = queueStats.get(game).split("-")[0];
            String losses = queueStats.get(game).split("-")[1];
            int games = Integer.valueOf(wins) + Integer.valueOf(losses);
        
            String percent = String.format("%.2f", Double.parseDouble(wins) * 100 / (Double.parseDouble(wins) + Double.parseDouble(losses)));
        
            if (count < 4) {
                gameString += GameQueueTypeUtils.getMapEmoji(game) + " " + GameQueueTypeUtils.prettyName(game) + " " + games + " games\n`(" + wins + "W/" + losses + "L) - " + percent + "% WR`\n";
            } else {
                otherWins += Integer.valueOf(wins);
                otherLosses += Integer.valueOf(losses);
            }
            count++;
        }
        
        if (otherWins > 0 || otherLosses > 0) {
            int otherGames = otherWins + otherLosses;
            String otherPercent = String.format("%.2f", (double) otherWins * 100 / otherGames);
            gameString += CustomEmojiHandler.getFormattedEmoji("special_mode") + "Others " + otherGames + " games\n`(" + otherWins + "W/" + otherLosses + "L) - " + otherPercent + "% WR`\n";
        }

        String kda = String.format("%.2f", overallStats.get("kills").avg()) + "/" + String.format("%.2f", overallStats.get("deaths").avg()) + "/" + String.format("%.2f", overallStats.get("assists").avg());
        String visionScore = String.format("%.2f", overallStats.get("vision_score").avg()) + " VS (" + 
                String.format("%.2f", overallStats.get("ward").avg()) + " placed / " +
                String.format("%.2f", overallStats.get("ward_killed").avg()) + " destroyed)";

        String cs =  String.format("%.2f", overallStats.get("cs").avg()) + " (" +
             String.format("%.2f", overallStats.get("cs_min").avg()) + " / min)";

        String damaString = String.format("%.2f", overallStats.get("damage").avg()) + " to champ / " +
            String.format("%.2f", overallStats.get("damage_building").avg()) + " to buildings";
    
        String arenaPlacement = "";
        if (isArena) {
            arenaPlacement = "1. " + overallStats.getOrDefault("arena_first", new Accumulator()).count + " times\n" +
                "2. " + overallStats.getOrDefault("arena_second", new Accumulator()).count + " times\n" +
                "3. " + overallStats.getOrDefault("arena_third", new Accumulator()).count + " times\n" +
                "avg. " + String.format("%.2f", overallStats.get("arena_placement").avg()) + " placement";
        }

        StringBuilder streak = new StringBuilder();

        int pentas = overallStats.getOrDefault("pentas", new Accumulator()).sum;
        int quadras = overallStats.getOrDefault("quadruples", new Accumulator()).sum;
        int triples = overallStats.getOrDefault("triples", new Accumulator()).sum;
        int doubles = overallStats.getOrDefault("doubles", new Accumulator()).sum;

        if (pentas > 0) 
            streak.append("Pentakills: ").append(pentas).append("\n");
        
        if (quadras > 0) 
            streak.append("Quadrakills: ").append(quadras).append("\n");
        
        if (triples > 0) 
            streak.append("Triplakills: ").append(triples).append("\n");
        
        if (doubles > 0) 
            streak.append("Doublekills: ").append(doubles).append("\n");

        String streakString = streak.toString().trim();

        String performance =
            (isArena ? "**Placement**\n`" + arenaPlacement + "`\n" : "") +
            "**KDA**\n`" + kda + 
            " (" + String.format("%.2f", overallStats.get("kill_participation").avg()) + "% kp & " +
            String.format("%.2f", overallStats.get("death_share").avg()) + "% dp)\n" +
            (!streakString.isEmpty() ? (streakString + "`\n") : "`") +
            (!isArena ? "**Vision Score**\n`" + visionScore + "`\n" : "") +
            (!isArena ? "**CS**\n`" + cs + "`\n" : "") +
            "**Damage**\n`" + damaString + "`\n" +
            (!isArena ? "**Gold Earned**\n`" + String.format("%.2f", overallStats.get("gold_earned").avg()) + "`\n" : "");

        String championString = "";
        if (!parameter.isShowChampion()) 
            championString = " with " + unique.get("champion").size() + " different champions";
        else {
            int champId = (int) unique.get("champion").toArray()[0];
            championString = " with " + CustomEmojiHandler.getFormattedEmoji(ChampionUtils.getChampion(champId).getName()) + " " + ChampionUtils.getChampion(champId).getName();
        }

        eb.setDescription(
            "Summoner has played **" + matches.size() + "** games " + championString +
            "\nA total of **" +  SafJNest.getFormattedDurationWithUnits(timePlayed) + "**\n" +
            "Oldest game: <t:" + (oldest / 1000) + ":R>\n" +
            "Newest game: <t:" + (newest / 1000) + ":R>"
        );
        
        if (!isArena) {
            eb.addField("Games", gameString, true);
            eb.addField("Roles", laneString , true);
        }

        if (!parameter.isShowChampion()) {
            String champStats = championStats.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue().getGames(), a.getValue().getGames()))
                .limit(6)
                .map(entry -> {
                    PlayerChampionStats stat = entry.getValue();
                    ChampionMastery mastery = masteries.get(stat.getChampion());
                    return LeagueMessageUtils.formatAdvancedData(stat, mastery);
                })
                .collect(Collectors.joining("\n"));

            eb.addField("Champions", champStats, false);
        }

        eb.addField("Average Performance", performance, false);

        List<Map.Entry<String, Integer>> sortedPings = pings.entrySet()
            .stream()
            .filter(e -> !e.getKey().equals("basic"))
            .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
            .limit(9)
            .collect(Collectors.toList());
        
        StringBuilder[] columns = {
            new StringBuilder(),
            new StringBuilder(),
            new StringBuilder()
        };
        
        for (int i = 0; i < sortedPings.size(); i++) {
            Map.Entry<String, Integer> entry = sortedPings.get(i);
        
            String pingName;
            switch (entry.getKey()) {
                case "command":
                    pingName = "Generic Ping";
                    break;
                default:
                    pingName = Arrays.stream(entry.getKey().replace("_", " ").split(" "))
                        .map(w -> w.isEmpty() ? "" :
                            Character.toUpperCase(w.charAt(0)) + w.substring(1).toLowerCase())
                        .collect(Collectors.joining(" "));
            }
        
            String line =
                CustomEmojiHandler.getFormattedEmoji(entry.getKey() + "_ping") + " " + pingName + "\n" +
                "`" + entry.getValue() + " total`\n";
        
            int columnIndex = i / 3;
            columns[columnIndex].append(line);
        }

        List<Integer> topD = dSpells.entrySet().stream()
            .filter(e -> e.getKey() != 0)
            .sorted(Map.Entry.<Integer, Integer>comparingByValue().reversed())
            .limit(3)
            .map((Map.Entry<Integer, Integer> e) -> e.getKey())
            .collect(Collectors.toList());
    
        List<Integer> topF = fSpells.entrySet().stream()
            .filter(e -> e.getKey() != 0)
            .sorted(Map.Entry.<Integer, Integer>comparingByValue().reversed())
            .limit(3)
            .map((Map.Entry<Integer, Integer> e) -> e.getKey())
            .collect(Collectors.toList());

        String spellCol1 = CustomEmojiHandler.getFormattedEmoji("q_") + " Ability 1\n`" + overallStats.get("q").sum + " times`\n" +
            CustomEmojiHandler.getFormattedEmoji("w_") + " Ability 2\n`" + overallStats.get("w").sum + " times`\n" +
            CustomEmojiHandler.getFormattedEmoji("e_") + " Ability 3\n`" + overallStats.get("e").sum + " times`\n" +
            CustomEmojiHandler.getFormattedEmoji("r_") + " Ultimate\n`" + overallStats.get("r").sum + " times`\n";
        
        String spellCol2 = CustomEmojiHandler.getFormattedEmoji("d_") + " Spell 1\n`" + dSpells.values().stream().mapToInt(Integer::intValue).sum() + " times`\n" +
            topD.stream().filter(id -> dSpells.get(id) > 0).map(id -> {
                return CustomEmojiHandler.getFormattedEmoji(id + "_") + " " + LeagueHandler.getSpellName(id) + "\n" + 
                "`" + dSpells.get(id) + " times`\n";
            }).collect(Collectors.joining());
        
        String spellCol3 = CustomEmojiHandler.getFormattedEmoji("f_") + " Spell 2\n`" + fSpells.values().stream().mapToInt(Integer::intValue).sum() + " times`\n" +
            topF.stream().filter(id -> fSpells.get(id) > 0).map(id -> {
                return CustomEmojiHandler.getFormattedEmoji(id + "_") + " " + LeagueHandler.getSpellName(id) + "\n" + 
                "`" + fSpells.get(id) + " times`\n";
            }).collect(Collectors.joining());

        eb.addField("Spell Performance", spellCol1, true);
        eb.addField(" ", spellCol2, true);
        eb.addField(" ", spellCol3, true);

        eb.addField("Pings Usage", columns[0].toString(), true);
        eb.addField(" ", columns[1].toString(), true);
        eb.addField(" ", columns[2].toString(), true);

        return eb;
    }

    private static EmbedBuilder getAllChampions(EmbedBuilder eb, List<Match> matches, Summoner summoner, String puuid, LeagueMessageParameter parameter) {

        if (matches.size() == 0) {
            eb.setDescription("Not enough games");
            return eb;
        }

        HashMap<Integer, PlayerChampionStats> championStats = new HashMap<>();
        HashMap<Integer, ChampionMastery> masteries = LeagueHandler.getMastery(summoner);

        HashMap<String, Set<Integer>> unique = new HashMap<>();
        unique.put("champion", new HashSet<>());

        for (Match match : matches) {
            for (Participant participant : match.participants) {
                if (!puuid.equals(participant.puuid)) continue;

                    unique.getOrDefault("champion", new HashSet<>()).add(participant.champion);
                    String kda = participant.kda;
                    int kills = Integer.parseInt(kda.split("/")[0]);
                    int deaths = Integer.parseInt(kda.split("/")[1]);
                    int assists = Integer.parseInt(kda.split("/")[2]);
                    championStats.computeIfAbsent(participant.champion, p -> new PlayerChampionStats(participant.champion)).add(kills, deaths, assists, participant.gain, participant.win);
            }
        }
        String champStats = championStats.entrySet().stream()
            .sorted((a, b) -> Integer.compare(b.getValue().getGames(), a.getValue().getGames()))
            .skip(parameter.getOffset())
            .limit(10)
            .map(entry -> {
                PlayerChampionStats stat = entry.getValue();
                ChampionMastery mastery = masteries.get(stat.getChampion());
                return LeagueMessageUtils.formatAdvancedData(stat, mastery);
            })
            .collect(Collectors.joining("\n"));

        int champs = unique.get("champion").size();
        eb.setDescription(
            "Summoner has played **" + matches.size() + "** games with " + champs + " different champions\n\n" +
            champStats
        );

        int pages = (int) Math.ceil((double) champs / 10);
        int currentPage = (parameter.getOffset() / 10) + 1;
        eb.setFooter("Page " + currentPage + " / " + pages);
        return eb;
    }

    private static EmbedBuilder getChampionOPGG(EmbedBuilder eb, List<Match> matches, Summoner s, String puuid, LeagueMessageParameter parameter) {
        for (Match match : matches) 
            eb = getOpggEmbedMatch(eb, match, s);
        
        int totalPages = MongoDB.countMatchHistory(puuid, parameter);
        int pages = (int) Math.ceil((double) totalPages / 5);
        int currentPage = (parameter.getOffset() / 5) + 1;
        eb.setFooter("Page " + currentPage + " / " + pages);
        eb.setThumbnail(null);
        return eb;
    }

    private static EmbedBuilder getObjectives(EmbedBuilder eb, List<Match> matches, Summoner summoner, String puuid) {
        HashMap<String, Integer> monsterKills = new HashMap<>();
        HashMap<String, Integer> monsterParticipation = new HashMap<>();
        HashMap<String, Integer> totalMonstersPerType = new HashMap<>();
        
        HashMap<String, Integer> buildingKills = new HashMap<>();
        HashMap<String, Integer> buildingParticipation = new HashMap<>();
        HashMap<String, Integer> totalBuildingsPerType = new HashMap<>();
        
        int totalGames = 0;
        
        for (Match match : matches) {
            Participant participant = match.participants.stream()
                    .filter(p -> puuid.equals(p.puuid))
                    .findFirst()
                    .orElse(null);
            
            if (participant == null) continue;
            
            String participantPuuid = participant.puuid;
            
            if (!match.events.has("participants") || (!match.events.has("monster_events") && !match.events.has("building_events"))) {
                continue;
            }
            
            JSONObject participantsMap = match.events.getJSONObject("participants");
            Integer participantId = null;
            
            for (String key : participantsMap.keySet()) {
                if (participantsMap.getString(key).equals(participantPuuid)) {
                    participantId = Integer.parseInt(key);
                    break;
                }
            }
            
            if (participantId == null) continue;
            totalGames++;
            
            if (match.events.has("monster_events")) {
                JSONArray monsters = match.events.getJSONArray("monster_events");
                for (Object objEvent : monsters) {
                    JSONObject event = (JSONObject) objEvent;
                    String monster = event.getString("monster");
                    String subtype = event.optString("subtype", "");
                    
                    String monsterKey;
                    if (!subtype.isEmpty()) {
                        monsterKey = subtype.toLowerCase().replace("_", " ");
                    } else {
                        monsterKey = monster.toLowerCase().replace("_", " ");
                    }
                    
                    totalMonstersPerType.put(monsterKey, totalMonstersPerType.getOrDefault(monsterKey, 0) + 1);
                    
                    int killer = event.getInt("killer");
                    JSONArray assists = event.getJSONArray("assists");
                    
                    boolean playerKilled = (killer == participantId);
                    boolean playerAssisted = false;
                    
                    for (Object assistObj : assists) {
                        if (assistObj instanceof Integer && (Integer) assistObj == participantId) {
                            playerAssisted = true;
                            break;
                        }
                    }
                    
                    if (playerKilled) {
                        monsterKills.put(monsterKey, monsterKills.getOrDefault(monsterKey, 0) + 1);
                    }
                    
                    if (playerKilled || playerAssisted) {
                        monsterParticipation.put(monsterKey, monsterParticipation.getOrDefault(monsterKey, 0) + 1);
                    }
                }
            
                if (match.events.has("building_events")) {
                    JSONArray buildings = match.events.getJSONArray("building_events");
                    for (Object objEvent : buildings) {
                        JSONObject event = (JSONObject) objEvent;
                        String building = event.getString("building");
                        
                        String buildingKey = building.toLowerCase().replace("_", " ");
                        
                        totalBuildingsPerType.put(buildingKey, totalBuildingsPerType.getOrDefault(buildingKey, 0) + 1);
                        
                        int killer = event.getInt("killer");
                        JSONArray assists = event.getJSONArray("assists");
                        
                        boolean playerKilled = (killer == participantId);
                        boolean playerAssisted = false;
                        
                        for (Object assistObj : assists) {
                            if (assistObj instanceof Integer && (Integer) assistObj == participantId) {
                                playerAssisted = true;
                                break;
                            }
                        }
                        
                        if (playerKilled) {
                            buildingKills.put(buildingKey, buildingKills.getOrDefault(buildingKey, 0) + 1);
                        }
                        
                        if (playerKilled || playerAssisted) {
                            buildingParticipation.put(buildingKey, buildingParticipation.getOrDefault(buildingKey, 0) + 1);
                        }
                    }
                }
            }
        }
        
        if (!totalMonstersPerType.isEmpty()) {
            StringBuilder monsterStats = new StringBuilder();
            for (String monsterType : totalMonstersPerType.keySet()) {
                int total = totalMonstersPerType.get(monsterType);
                int kills = monsterKills.getOrDefault(monsterType, 0);
                int participation = monsterParticipation.getOrDefault(monsterType, 0);
                double avgPerGame = totalGames > 0 ? (double) total / totalGames : 0;
                
                if (participation > 0) {
                    String displayName = LeagueMessageUtils.capitalizeFirstLetter(monsterType);
                    monsterStats.append(String.format("**%s**: %dK %dP (%.1f avg)\n", 
                        displayName, kills, participation, avgPerGame));
                }
            }
            
            if (monsterStats.length() > 0) {
                String statsText = monsterStats.toString();
                if (statsText.length() > 1000) {
                    statsText = statsText.substring(0, 997) + "...";
                }
                eb.addField("🐉 Monsters", statsText, false);
            }
        }
        
        if (!totalBuildingsPerType.isEmpty()) {
            StringBuilder buildingStats = new StringBuilder();
            for (String buildingType : totalBuildingsPerType.keySet()) {
                int total = totalBuildingsPerType.get(buildingType);
                int kills = buildingKills.getOrDefault(buildingType, 0);
                int participation = buildingParticipation.getOrDefault(buildingType, 0);
                double avgPerGame = totalGames > 0 ? (double) total / totalGames : 0;
                
                if (participation > 0) {
                    String displayName = LeagueMessageUtils.capitalizeFirstLetter(buildingType);
                    buildingStats.append(String.format("**%s**: %dK %dP (%.1f avg)\n", 
                        displayName, kills, participation, avgPerGame));
                }
            }
            
            if (buildingStats.length() > 0) {
                String statsText = buildingStats.toString();
                if (statsText.length() > 1000) {
                    statsText = statsText.substring(0, 997) + "...";
                }
                eb.addField("🏰 Buildings", statsText, false);
            }
        }
        
        return eb;
    }
}
