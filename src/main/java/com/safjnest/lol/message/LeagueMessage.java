package com.safjnest.lol.message;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.core.Bot;
import com.safjnest.lol.LeagueHandler;
import com.safjnest.lol.model.ChampionStatistics;
import com.safjnest.lol.model.Filter;
import com.safjnest.lol.model.ChampionStatistics.LaneStat;
import com.safjnest.lol.model.ChampionStatistics.Matchup;
import com.safjnest.lol.model.match.LiveGame;
import com.safjnest.lol.model.match.Match;
import com.safjnest.lol.model.match.Participant;
import com.safjnest.lol.model.statistics.ProfileStatistics;
import com.safjnest.lol.model.statistics.ProfileMatchups;
import com.safjnest.lol.model.statistics.Stats;
import com.safjnest.lol.model.summoner.Mastery;
import com.safjnest.lol.utils.ChampionUtils;
import com.safjnest.lol.utils.GameQueueTypeUtils;
import com.safjnest.lol.utils.LaneTypeUtils;
import com.safjnest.lol.utils.LeagueMessageUtils;
import com.safjnest.lol.utils.LeagueShardUtils;
import com.safjnest.lol.utils.RankProgressUtils;
import com.safjnest.lol.utils.SeasonUtils;
import com.safjnest.lol.utils.TierDivisionUtils;
import com.safjnest.lol.service.ChampionService;
import com.safjnest.lol.service.MatchService;
import com.safjnest.lol.service.SummonerService;
import com.safjnest.lol.service.ProfileService;
import com.safjnest.lol.queue.scheduler.ComputeScheduler;
import com.safjnest.model.customemoji.CustomEmojiHandler;
import com.safjnest.nosql.MongoDB;
import com.safjnest.sql.QueryRecord;
import com.safjnest.utils.DateHandler;
import com.safjnest.utils.SafJNest;
import com.safjnest.utils.log.BotLogger;

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
import no.stelar7.api.r4j.pojo.lol.league.LeagueEntry;
import no.stelar7.api.r4j.pojo.lol.staticdata.champion.StaticChampion;
import com.safjnest.lol.model.summoner.Summoner;
import com.safjnest.lol.model.match.RankProgress;

public class LeagueMessage {

    public static final String BUTTON_ID_PREFIX = "lol";
    private static final ProfileService PROFILE_SERVICE = new ProfileService();
    private static final ChampionService CHAMPION_SERVICE = new ChampionService();

    private static Object[] build(String userId, Summoner summoner, String puuid, LeagueMessageParameter parameter) {
        MessageEmbed embed = null;
        List<MessageTopLevelComponent> components = new ArrayList<>();
        if (summoner != null
                && parameter.getMessageType() != LeagueMessageType.LIVEGAME
                && parameter.getMessageType() != LeagueMessageType.OPGG)
            LeagueHandler.updateSummonerMongo(summoner);
        switch (parameter.getMessageType()) {
            case PROFILE:
                embed = getSummonerEmbed(summoner, parameter).build();
                components = getSummonerButtons(summoner, userId, parameter);
                break;
            case LIVEGAME:
                LiveGame liveGame = SummonerService.getLiveGame(summoner.puuid(), summoner.region());
                if (liveGame == null) liveGame = LiveGame.empty();
                List<LiveGame.Participant> users = liveGame.participants();
                StringSelectMenu menu = LeagueMessage.getLivegameMenu(summoner, users);

                embed = LeagueMessage.getLivegameEmbed(summoner, liveGame, users).build();
                components = new ArrayList<>(composeButtons(summoner, userId != null ? userId : null, new LeagueMessageParameter(LeagueMessageType.LIVEGAME)));
                if (menu != null) 
                    components.add(0, ActionRow.of(menu));
                
                break;
            case OPGG:
                List<Match> matches = getOpggMatches(summoner, parameter);
                Match selectedMatch = getSelectedOpggMatch(summoner, parameter);
                if (selectedMatch != null) {
                    embed = getCanonicalOpggEmbedMatch(summoner, selectedMatch).build();
                    components = getCanonicalOpggButtons(summoner, userId, parameter, matches, selectedMatch);
                } else {
                    embed = getCanonicalOpggEmbed(summoner, parameter, matches).build();
                    components = getCanonicalOpggButtons(summoner, userId, parameter, matches, null);
                }
                break;
            case OVERVIEW:
            case MATCHUP:
            case OVERVIEW_CHAMPIONS:
            case OVERVIEW_OPGG:
                embed = buildEmbedChampion(userId, summoner, puuid, parameter);
                components = getChampionButtons(userId, summoner, parameter);
                break;
            case CHAMPIONS_BY_WINRATE:
            case CHAMPIONS_BY_PICKRATE:
            case CHAMPIONS_BY_BANRATE:
                List<ChampionStatistics> champions = CHAMPION_SERVICE.getStatisticsAll(parameter.toFilter()).values().stream().toList();
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

    private static List<Match> getOpggMatches(Summoner summoner, LeagueMessageParameter parameter) {
        List<Match> matches = new ArrayList<>();
        int limit = parameter.getMessageType().getPageItem();
        List<String> gameIds = getMatchIds(summoner, parameter.getQueueType(), parameter.getOffset());
        for (int position = 0; position < gameIds.size(); position++) {
            if (matches.size() >= limit) break;
            Match match = MatchService.get(gameIds.get(position), summoner.region());
            if (match != null) matches.add(match);
        }
        MatchService.upsertRankProgress(matches, summoner.region());
        return matches;
    }

    private static Match getSelectedOpggMatch(Summoner summoner, LeagueMessageParameter parameter) {
        String selectedMatchId = parameter.getSelectedMatchId();
        return selectedMatchId == null ? null : MatchService.get(selectedMatchId, summoner.region());
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

        String centerName = parameter.getMessageType() == LeagueMessageType.OPGG
            ? getStoredRiotId(s)
            : (s.name().isBlank()
                ? SummonerService.getRiotAccount(s.puuid(), s.region()).getName()
                : s.name());
        Button center = Button.primary(BUTTON_ID_PREFIX + "-center-" + s.puuid() + "#" + s.region().name(), centerName);
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
        String riotId = getStoredRiotId(s);
        String authorName = riotId.contains("#") ? riotId : riotId;

        EmbedBuilder builder = new EmbedBuilder();
        builder.setAuthor(authorName, null, LeagueHandler.getSummonerProfilePic(s));
        builder.setColor(Bot.getColor());
        builder.setThumbnail(LeagueHandler.getSummonerProfilePic(s));

        String description = "Summoner is level **" + s.level() + "** on " + LeagueShardUtils.getRegionFlag(s.region()) + s.region().getRealmValue() + " server.";
        builder.setDescription(description);

        builder.addField("Solo/duo", LeagueHandler.getSoloQStats(s), true);
        builder.addField("Flex", LeagueHandler.getFlexStats(s), true);

        String masteryString = "";
        for(int i = 1; i < 4; i++)
            masteryString += LeagueHandler.getMastery(s, i) + "\n";

        builder.addField("Highest Masteries", masteryString, false);
        ProfileStatistics statistics = profileStatistics(s, parameter);
        if (statistics == null) {
            builder.addField("Statistics", "Statistics are being prepared for this filter.", false);
        } else {
            builder = addLegacyProfileStats(builder, statistics, s, parameter);
        }
        builder.addField("Last update", statistics == null ? "not available" : formatLastUpdate(statistics.lastUpdate), false);
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
        return getCanonicalOpggMenu(summoner, getOpggMatches(summoner, parameter), parameter);
    }

    private static StringSelectMenu getCanonicalOpggMenu(Summoner summoner, List<Match> matches, LeagueMessageParameter parameter) {
        ArrayList<SelectOption> options = new ArrayList<>();
        for (Match match : matches) {
            try {
                Participant me = match.participants.stream()
                        .filter(participant -> summoner.puuid().equals(participant.puuid))
                        .findFirst()
                        .orElse(null);
                if (me == null) continue;

                Emoji icon = ChampionUtils.getEmojiByChampion(me.champion);
                String queueName = GameQueueTypeUtils.prettyName(match.queue);
                String label = Math.max(0L, match.getDuration() / 60000L) + " minutes " + queueName;
                StaticChampion champion = ChampionUtils.getChampion(me.champion);
                String championName = champion == null ? String.valueOf(me.champion) : champion.getName();
                String description = "As " + championName
                        + " (" + me.kda + " " + me.cs + " CS)";
                String fullGameId = match.gameId;
                boolean isDefault = parameter.getSelectedMatchId() != null
                        && parameter.getSelectedMatchId().equals(fullGameId);
                SelectOption option = SelectOption.of(label, fullGameId + "#" + summoner.puuid())
                        .withDescription(description)
                        .withDefault(isDefault);
                options.add(icon == null ? option : option.withEmoji(icon));
            } catch (Exception ignored) {
            }
        }

        if (options.isEmpty()) return null;
        return StringSelectMenu.create(LeagueMessage.BUTTON_ID_PREFIX + "-opggselect")
                .setPlaceholder("Select a game")
                .setMaxValues(1)
                .addOptions(options)
                .build();
    }

    public static List<String> getMatchIds(Summoner s, GameQueueType queue, int index) {
        List<String> gameIds = new ArrayList<>();
        no.stelar7.api.r4j.pojo.lol.summoner.Summoner riotSummoner = SummonerService.getRiotSummoner(s.puuid(), s.region());
        List<String> allIds = riotSummoner == null ? List.of() : MatchService.getRecentIds(riotSummoner, queue, index);

        for (String gameId : allIds) {
            if (gameId.split("_")[0].equalsIgnoreCase(s.region().toString()))
                gameIds.add(gameId);
        }

        if (gameIds.size() > 5) return gameIds;

        for (String gameId : allIds)
            if (!gameIds.contains(gameId)) gameIds.add(gameId);

        return gameIds;
    }

    private static EmbedBuilder getOpggEmbedMatch(
            EmbedBuilder eb,
            Match match,
            Summoner summoner,
            List<QueryRecord> queryResult) {
        Participant me = null;
        for(Participant mp : match.participants){
            if(mp.puuid.equals(summoner.puuid())){
                me = mp;
                break;
            }
        }

        ArrayList<String> blue = new ArrayList<>();
        ArrayList<String> red = new ArrayList<>();
        for(Participant searchMe : match.participants) {
            StaticChampion champion = ChampionUtils.getChampion(searchMe.champion);
            String participantString = CustomEmojiHandler.getFormattedEmoji(champion == null ? String.valueOf(searchMe.champion) : champion.getName())
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
        HashMap<Participant, HashMap<String, String>> totalStats = new HashMap<>();
        HashMap<TeamType, HashMap<String, String>> teamStats = new HashMap<>();
        teamStats.put(TeamType.BLUE, new HashMap<>());
        teamStats.put(TeamType.RED, new HashMap<>());
        switch (match.queue){
            case STRAWBERRY:
                content = CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.champion)) + " Level: " + 
                        (me.championLevel != null && me.championLevel > 0 ? me.championLevel : 1) +
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
                            (mt.championLevel != null && mt.championLevel > 0 ? mt.championLevel : 1) +
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
                    StaticChampion champion = ChampionUtils.getChampion(mt.champion);
                    String championName = champion == null ? String.valueOf(mt.champion) : champion.getName();
                    String name = mt.riotId == null || mt.riotId.isBlank()
                            ? " **" + (mt.puuid == null ? "Unknown" : mt.puuid) + "**"
                            : " **" + mt.riotId + (mt.riotTag == null || mt.riotTag.isBlank() ? "" : "#" + mt.riotTag) + "**";
                    name += getOpggRankIcon(mt, match);
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
                    if (prova.containsKey(team)) prova.get(team).add(CustomEmojiHandler.getFormattedEmoji(championName) + name);
                    positions.put(mt.subTeamPlacement, team);

                    HashMap<String, String> stats = new HashMap<>();
                    stats.put("damageDealt", String.valueOf(mt.damage));
                    if (mt.damageTaken != null) stats.put("damageTaken", String.valueOf(mt.damageTaken));
                    totalStats.put(mt, stats);

                    TeamType currentTeam = me.subTeam == mt.subTeam ? TeamType.BLUE : TeamType.RED;
                    HashMap<String, String> currentStats = teamStats.get(currentTeam);
                    currentStats.put("kills", String.valueOf(getKdaValue(mt.kda, 0) + getTeamValue(currentStats, "kills")));
                    currentStats.put("damageDealt", String.valueOf(mt.damage + getTeamValue(currentStats, "damageDealt")));
                    if (mt.damageTaken != null)
                        currentStats.put("damageTaken", String.valueOf(mt.damageTaken + getTeamValue(currentStats, "damageTaken")));
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
                HashMap<String, String> arenaPersonalStats = totalStats.get(me);
                int arenaTeamKills = Math.max(1, getTeamValue(teamStats.get(TeamType.BLUE), "kills"));
                String arenaKillParticipation = String.format("%.1f", (double) (getKdaValue(me.kda, 0) + getKdaValue(me.kda, 2)) / arenaTeamKills * 100);
                String arenaPersonalStatsText = "**KDA**: " + me.kda + " (" + arenaKillParticipation + "% kill participation)\n"
                        + "**Damage Dealt to champion**: " + LeagueMessageUtils.formatNumber(arenaPersonalStats.get("damageDealt")) + " (" + LeagueMessageUtils.getPosition(totalStats, arenaPersonalStats, "damageDealt") + "th in the game)\n"
                        + "**Damage Taken**: " + (arenaPersonalStats.containsKey("damageTaken")
                            ? LeagueMessageUtils.formatNumber(arenaPersonalStats.get("damageTaken")) + " (" + LeagueMessageUtils.getPosition(totalStats, arenaPersonalStats, "damageTaken") + "th in the game)"
                            : "Unavailable") + "\n";
                eb.addField("Personal Stats", arenaPersonalStatsText, false);

                eb.addField("Build", content, false);
                eb.addField("Top 4", blueTeam, true);
                eb.addField("Others", redTeam, true);
                break;

            default:
                if (match.bans != null) for (Map.Entry<TeamType, List<Integer>> entry : match.bans.entrySet()) {
                    if (!teamStats.containsKey(entry.getKey())) continue;
                    String banText = "**Bans**\n";
                    if (entry.getValue() != null) for (Integer championId : entry.getValue()) {
                        if (championId == null || championId < 0) banText += CustomEmojiHandler.getFormattedEmoji("0") + " ";
                        else banText += ChampionUtils.getFormattedEmojiByChampion(championId) + " ";
                    }
                    teamStats.get(entry.getKey()).put("bans", banText);
                }
                for (TeamType team : List.of(TeamType.BLUE, TeamType.RED)) {
                    teamStats.get(team).putIfAbsent("bans", "**Bans**\n");
                    teamStats.get(team).putIfAbsent("champions", "**Picks**\n");
                }

                for (Participant participant : match.participants) {
                    if (!teamStats.containsKey(participant.team)) continue;

                    HashMap<String, String> currentStats = teamStats.get(participant.team);
                    currentStats.put("kills", String.valueOf(getKdaValue(participant.kda, 0) + getTeamValue(currentStats, "kills")));
                    currentStats.put("towers", String.valueOf(participant.turretKills + getTeamValue(currentStats, "towers")));
                    currentStats.put("gold", String.valueOf(participant.goldEarned + getTeamValue(currentStats, "gold")));

                    StaticChampion champion = ChampionUtils.getChampion(participant.champion);
                    String championName = champion == null ? String.valueOf(participant.champion) : champion.getName();
                    String participantName = participant.riotId == null || participant.riotId.isBlank()
                            ? (participant.puuid == null ? "Unknown" : participant.puuid)
                            : participant.riotId + (participant.riotTag == null || participant.riotTag.isBlank() ? "" : "#" + participant.riotTag);
                    String participantKda = participant.kda + " (" + participant.cs + " CS)";
                    String championText = currentStats.get("champions")
                            + CustomEmojiHandler.getFormattedEmoji(championName) + " **" + participantName + "** "
                            + getOpggRankIcon(participant, match) + "\n" + participantKda + "\n";
                    currentStats.put("champions", championText);

                    HashMap<String, String> stats = new HashMap<>();
                    stats.put("damageDealt", String.valueOf(participant.damage));
                    if (participant.damageTaken != null) stats.put("damageTaken", String.valueOf(participant.damageTaken));
                    stats.put("heal", String.valueOf(participant.healing));
                    stats.put("vision", String.valueOf(participant.visionScore));
                    totalStats.put(participant, stats);
                }

                String blueSide = CustomEmojiHandler.getFormattedEmoji("kda") + getTeamValue(teamStats.get(TeamType.BLUE), "kills")
                        + " ∙ " + CustomEmojiHandler.getFormattedEmoji("tower") + getTeamValue(teamStats.get(TeamType.BLUE), "towers")
                        + " ∙ " + CustomEmojiHandler.getFormattedEmoji("golds2") + " "
                        + LeagueMessageUtils.formatNumber(String.valueOf(getTeamValue(teamStats.get(TeamType.BLUE), "gold"))) + "\n"
                        + teamStats.get(TeamType.BLUE).get("bans") + "\n\n"
                        + teamStats.get(TeamType.BLUE).get("champions");
                String redSide = CustomEmojiHandler.getFormattedEmoji("kda") + getTeamValue(teamStats.get(TeamType.RED), "kills")
                        + " ∙ " + CustomEmojiHandler.getFormattedEmoji("tower") + getTeamValue(teamStats.get(TeamType.RED), "towers")
                        + " ∙ " + CustomEmojiHandler.getFormattedEmoji("golds2") + " "
                        + LeagueMessageUtils.formatNumber(String.valueOf(getTeamValue(teamStats.get(TeamType.RED), "gold"))) + "\n"
                        + teamStats.get(TeamType.RED).get("bans") + "\n\n"
                        + teamStats.get(TeamType.RED).get("champions");

                eb.setDescription((me.win ? "Win" : "Lose") + " as " + CustomEmojiHandler.getFormattedEmoji(getChampionName(me.champion))
                        + " " + getChampionName(me.champion) + " in " + (match.getDuration() / 60000) + " minutes\n"
                        + getOpggMatchTitle(match, me, queryResult));
                eb.addField("Blue Side", blueSide, true);
                eb.addField("Red Side", redSide, true);

                HashMap<String, String> personalStats = totalStats.get(me);
                if (personalStats == null) {
                    personalStats = new HashMap<>();
                    personalStats.put("damageDealt", String.valueOf(me.damage));
                }
                int totalCreeps = me.cs;
                int defaultTeamKills = Math.max(1, getTeamValue(teamStats.get(me.team), "kills"));
                String killParticipation = String.format("%.1f", (double) (getKdaValue(me.kda, 0) + getKdaValue(me.kda, 2)) / defaultTeamKills * 100);
                String personalStatsText = "**KDA**: " + me.kda + " (" + killParticipation + "% kill participation)\n"
                        + "**CS**: " + totalCreeps + " (" + String.format("%.1f", totalCreeps / Math.max(1.0, match.getDuration() / 60000.0)) + " CS/min)\n"
                        + "**Vision Score**: " + me.visionScore + " (" + me.ward + " wards placed)\n"
                        + "**Damage Dealt to champion**: " + LeagueMessageUtils.formatNumber(personalStats.get("damageDealt"))
                        + " (" + LeagueMessageUtils.getPosition(totalStats, personalStats, "damageDealt") + "th in the game)\n";
                eb.addField("Personal Stats", personalStatsText, false);

                content = CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.summonerSpell1) + "_") + getFormattedPrimaryRunes(me) + "\n"
                        + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.summonerSpell2) + "_") + getFormattedSecondaryRunes(me) + "\n"
                        + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.roleQuestId)) + " "
                        + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.item6)) + " | "
                        + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.item0)) + " "
                        + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.item1)) + " "
                        + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.item2)) + " "
                        + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.item3)) + " "
                        + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.item4)) + " "
                        + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.item5));
                eb.addField("Build", content, false);
                break;
        }
        return eb;
    }

    private static EmbedBuilder getOpggEmbedMatchPreview(EmbedBuilder eb,  Match match, Summoner summoner, List<QueryRecord> queryResult) {
        Participant me = null;
        for (Participant participant : match.participants) {
            if (summoner.puuid().equals(participant.puuid)) {
                me = participant;
                break;
            }
        }
        if (me == null) return eb;

        List<String> blue = new ArrayList<>();
        List<String> red = new ArrayList<>();
        for (Participant participant : match.participants) {
            String participantString = CustomEmojiHandler.getFormattedEmoji(getChampionName(participant.champion))
                    + " " + participant.kda;
            if (participant.team == TeamType.BLUE) blue.add(participantString);
            else if (participant.team == TeamType.RED) red.add(participantString);
        }

        String kda = me.kda;
        String date = "<t:" + ((match.timeStart / 1000) + (match.getDuration() / 1000)) + ":R>";
        switch (match.queue) {
            case STRAWBERRY:
                String swarmContent = CustomEmojiHandler.getFormattedEmoji(getChampionName(me.champion))
                        + " Level: " + (me.championLevel != null && me.championLevel > 0 ? me.championLevel : 1)
                        + " | " + CustomEmojiHandler.getFormattedEmoji("golds") + me.goldEarned + "\n"
                        + date + " | ** " + LeagueMessageUtils.getFormattedDuration(match.getDuration()) + "**\n"
                        + getSwarmItemsLine(me);
                eb.addField("Swarm: " + (me.win ? "WIN" : "LOSE"), swarmContent, true);

                StringBuilder swarmTeam = new StringBuilder();
                for (Participant participant : match.participants) {
                    swarmTeam.append(CustomEmojiHandler.getFormattedEmoji(getChampionName(participant.champion)))
                            .append(" Level: ").append(participant.championLevel != null && participant.championLevel > 0 ? participant.championLevel : 1)
                            .append(" | ").append(CustomEmojiHandler.getFormattedEmoji("golds"))
                            .append(participant.goldEarned).append("\n");
                }
                eb.addField("Swarm Team", swarmTeam.toString(), true);
                eb.addBlankField(true);
                break;
            case CHERRY:
                String arenaContent = CustomEmojiHandler.getFormattedEmoji(getChampionName(me.champion)) + kda + "\n"
                        + date + " | **" + LeagueMessageUtils.getFormattedDuration(match.getDuration()) + "**\n"
                        + CustomEmojiHandler.getFormattedEmoji(me.summonerSpell1 + "_")
                        + CustomEmojiHandler.getFormattedEmoji("a" + getAugment(me, 0)) + " "
                        + CustomEmojiHandler.getFormattedEmoji("a" + getAugment(me, 1)) + "\n"
                        + CustomEmojiHandler.getFormattedEmoji(me.summonerSpell2 + "_")
                        + CustomEmojiHandler.getFormattedEmoji("a" + getAugment(me, 2)) + " "
                        + CustomEmojiHandler.getFormattedEmoji("a" + getAugment(me, 3)) + "\n"
                        + getItemsLine(me);

                Map<String, List<String>> teams = new HashMap<>();
                Map<Integer, String> positions = new HashMap<>();
                for (Participant participant : match.participants) {
                    String team = getArenaTeam(participant.subTeam);
                    if (team.isEmpty()) continue;
                    teams.computeIfAbsent(team, ignored -> new ArrayList<>())
                            .add(CustomEmojiHandler.getFormattedEmoji(getChampionName(participant.champion)));
                    positions.put(participant.subTeamPlacement, team);
                }

                boolean is3v3 = teams.getOrDefault("teamporos", List.of()).size() == 3;
                int spacing = is3v3 ? 3 : 2;
                int teamCount = is3v3 ? 3 : 4;
                StringBuilder blueTeam = new StringBuilder();
                StringBuilder redTeam = new StringBuilder();
                for (int position = 1; position <= 8; position++) {
                    String team = positions.get(position);
                    List<String> champions = teams.get(team);
                    if (champions == null || champions.size() < spacing) continue;
                    String value = String.join("", champions.subList(0, spacing));
                    String space = is3v3 ? "\n" : position % 2 == 0 ? "\n\n" : "\n";
                    if (position <= teamCount) blueTeam.append(CustomEmojiHandler.getFormattedEmoji(team)).append(value).append(space);
                    else redTeam.append(CustomEmojiHandler.getFormattedEmoji(team)).append(value).append(space);
                }

                String cherryTitle = is3v3 ? "ARENA 3v3" : "ARENA";
                eb.addField(cherryTitle + ": " + (me.win ? "WIN" : "LOSE"), arenaContent, true);
                eb.addField(is3v3 ? "Top 3" : "Top 4", blueTeam.toString(), true);
                eb.addField("Others", redTeam.toString(), true);
                break;
            default:
                String matchTitle = getOpggMatchTitle(match, me, queryResult);
                String normalContent = CustomEmojiHandler.getFormattedEmoji(getChampionName(me.champion)) + kda
                        + " | **Vision: **" + me.visionScore + "\n"
                        + date + " | ** " + LeagueMessageUtils.getFormattedDuration(match.getDuration()) + "**\n"
                        + CustomEmojiHandler.getFormattedEmoji(me.summonerSpell1 + "_") + getFormattedPrimaryRunes(me) + "\n"
                        + CustomEmojiHandler.getFormattedEmoji(me.summonerSpell2 + "_") + getFormattedSecondaryRunes(me) + "\n"
                        + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.roleQuestId)) + " "
                        + CustomEmojiHandler.getFormattedEmoji(String.valueOf(me.item6)) + " | "
                        + getItemsLine(me);
                eb.addField(matchTitle, normalContent, true);
                eb.addField("Blue Side", String.join("\n", blue), true);
                eb.addField("Red Side", String.join("\n", red), true);
                break;
        }
        return eb;
    }

    private static String getItemsLine(Participant participant) {
        return CustomEmojiHandler.getFormattedEmoji(String.valueOf(participant.item0)) + " "
                + CustomEmojiHandler.getFormattedEmoji(String.valueOf(participant.item1)) + " "
                + CustomEmojiHandler.getFormattedEmoji(String.valueOf(participant.item2)) + " "
                + CustomEmojiHandler.getFormattedEmoji(String.valueOf(participant.item3)) + " "
                + CustomEmojiHandler.getFormattedEmoji(String.valueOf(participant.item4)) + " "
                + CustomEmojiHandler.getFormattedEmoji(String.valueOf(participant.item5));
    }

    private static String getSwarmItemsLine(Participant participant) {
        return getItemsLine(participant) + " "
                + CustomEmojiHandler.getFormattedEmoji(String.valueOf(participant.item6));
    }

    private static int getAugment(Participant participant, int index) {
        return participant.augments != null && index < participant.augments.size() ? participant.augments.get(index) : 0;
    }

    private static String getArenaTeam(int subTeam) {
        return switch (subTeam) {
            case 1 -> "teamporos";
            case 2 -> "teamminions";
            case 3 -> "teamscuttles";
            case 4 -> "teamkrugs";
            case 5 -> "teamraptors";
            case 6 -> "teamsentinels";
            case 7 -> "teamwolves";
            case 8 -> "teamgromps";
            default -> "";
        };
    }

    private static String getChampionName(int championId) {
        StaticChampion champion = ChampionUtils.getChampion(championId);
        return champion == null ? String.valueOf(championId) : champion.getName();
    }

    private static String getOpggRankIcon(Participant participant, Match match) {
        if (participant.rankProgress != null && participant.rankProgress.rank != null)
            return TierDivisionUtils.getFormattedRank(participant.rankProgress.rank, true);
        if (participant.puuid == null || match.leagueShard == null) return CustomEmojiHandler.getFormattedEmoji("unranked");
        return LeagueHandler.getRankIcon(LeagueHandler.getRankEntry(participant.puuid, match.leagueShard));
    }

    private static int getKdaValue(String kda, int index) {
        if (kda == null) return 0;
        String[] values = kda.split("/");
        if (index < 0 || index >= values.length) return 0;
        try {
            return Integer.parseInt(values[index]);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static int getTeamValue(Map<String, String> values, String key) {
        if (values == null) return 0;
        try {
            return Integer.parseInt(values.getOrDefault(key, "0"));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String getFormattedLpGain(int gain) {
        return (gain > 0 ? "+" : "") + gain + " LP";
    }

    private static boolean hasRankSnapshot(QueryRecord row) {
        return RankProgressUtils.hasCurrentSnapshot(getRankProgress(row));
    }

    private static RankProgress getRankProgress(QueryRecord row) {
        QueryRecord value = row == null ? null : row.getAsRecord("rankProgress");
        TierDivisionType rank = value == null ? null : value.getAsTier("rank");
        if (rank == null || value == null || !value.containsKey("lp")) return null;
        Integer gain = value.containsKey("gain") && value.getValue("gain") != null ? value.getAsInt("gain") : null;
        TierDivisionType previousRank = value.getAsTier("previousRank");
        Integer previousLp = value.containsKey("previousLp") && value.getValue("previousLp") != null ? value.getAsInt("previousLp") : null;
        return new RankProgress(rank, value.getAsInt("lp"), gain, previousRank, previousLp);
    }

    private static boolean isRankedSolo(Match match) {
        return match != null && GameQueueTypeUtils.isRankedSolo(match.queue);
    }

    private static String getOpggMatchTitle(Match match, Participant participant, List<QueryRecord> result) {
        String title = GameQueueTypeUtils.prettyName(match.queue) + ": " + (participant.win ? "WIN" : "LOSE");
        for (int index = 0; index < result.size(); index++) {
            QueryRecord row = result.get(index);
            if (!match.gameId.equals(row.getAsString("game_id"))) continue;

            if (!hasRankSnapshot(row)) return title + " ? LP";
            RankProgress progress = getRankProgress(row);
            if (progress == null) return title + " ? LP";
            TierDivisionType rank = progress.rank;
            Integer gainValue = progress.gain;

            if (RankProgressUtils.isPlacement(progress)) {
                title = "Placement: " + (participant.win ? "WIN" : "LOSE");
            } else if (RankProgressUtils.isPromotion(progress)) {
                title = "Promoted to " + TierDivisionUtils.getFormattedRank(rank, true) + " " + progress.lp + "LP";
            } else if (RankProgressUtils.isDemotion(progress)) {
                title = "Demoted to " + TierDivisionUtils.getFormattedRank(rank, true) + " " + progress.lp + "LP";
            } else if (gainValue == null || gainValue == 0 && !RankProgressUtils.hasPreviousSnapshot(progress)) {
                return title + " ? LP";
            } else if (!row.getAsBoolean("win") && gainValue == 0) {
                title += " -0 LP";
            } else {
                title += " " + getFormattedLpGain(gainValue);
            }
            return title;
        }

        if (isRankedSolo(match)) return title + " ? LP";
        RankProgress progress = participant.rankProgress;
        if (!RankProgressUtils.hasCurrentSnapshot(progress) || progress.gain == null) return title;
        if (RankProgressUtils.isPlacement(progress)) return "Placement: " + (participant.win ? "WIN" : "LOSE");
        if (progress.gain == 0 && !RankProgressUtils.hasPreviousSnapshot(progress)) return title + " ? LP";
        if (!participant.win && progress.gain == 0) return title + " -0 LP";
        title += " " + getFormattedLpGain(progress.gain);
        return title;
    }

    private static String getFormattedPrimaryRunes(Participant p) {
        if (p.primaryRunes == null || p.primaryRunes.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append(CustomEmojiHandler.getFormattedEmoji(LeagueHandler.getFatherRuneById(p.primaryRunes.get(0))));
        for (int i = 1; i < p.primaryRunes.size(); i++) {
            sb.append(CustomEmojiHandler.getFormattedEmoji(String.valueOf(p.primaryRunes.get(i))));
            if (i < 1) sb.append(" ");
        }
        return sb.toString();
    }

    private static String getFormattedSecondaryRunes(Participant p) {
        if (p.secondaryRunes == null || p.secondaryRunes.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append(CustomEmojiHandler.getFormattedEmoji(LeagueHandler.getFatherRuneById(p.secondaryRunes.get(0))));
        for (int i = 1; i < p.secondaryRunes.size(); i++) {
            sb.append(CustomEmojiHandler.getFormattedEmoji(String.valueOf(p.secondaryRunes.get(i))));
            if (i < 1) sb.append(" ");
        }
        return sb.toString();
    }

    public static EmbedBuilder getOpggEmbed(Summoner s, LeagueMessageParameter parameter) {
        return getCanonicalOpggEmbed(s, parameter, getOpggMatches(s, parameter));
    }

    private static EmbedBuilder getCanonicalOpggEmbed(Summoner summoner, LeagueMessageParameter parameter, List<Match> matches) {
        LeagueShard shard = summoner.region();
        List<QueryRecord> queryResult = MatchService.getSummonerData(summoner.puuid(), summoner.region());
        EmbedBuilder embed = new EmbedBuilder();
        embed.setAuthor(getOpggRiotId(summoner, matches), null, LeagueHandler.getSummonerProfilePic(summoner));
        embed.setColor(Bot.getColor());
        embed.setTitle("Showing matches from " + LeagueShardUtils.getRegionFlag(shard) + " " + shard.getRealmValue());

        for (Match match : matches) {
            try {
                if (match.participants == null || match.participants.isEmpty()) continue;
                embed = getOpggEmbedMatchPreview(embed, match, summoner, queryResult);
            } catch (Exception exception) {
                BotLogger.error("OPGG match rendering failed for " + match.gameId + ": " + exception.getMessage());
            }
        }

        if (embed.getFields().isEmpty()) embed.setDescription("No games found");
        return embed;
    }

    private static EmbedBuilder getCanonicalOpggEmbedMatch(Summoner summoner, Match match) {
        List<QueryRecord> queryResult = MatchService.getSummonerData(summoner.puuid(), summoner.region());
        EmbedBuilder embed = new EmbedBuilder();
        embed.setAuthor(getOpggRiotId(summoner, List.of(match)), null, LeagueHandler.getSummonerProfilePic(summoner));
        embed.setColor(Bot.getColor());
        embed.setTitle(GameQueueTypeUtils.prettyName(match.queue));
        return getOpggEmbedMatch(embed, match, summoner, queryResult);
    }

    private static String getOpggRiotId(Summoner summoner, List<Match> matches) {
        if (matches != null) for (Match match : matches) {
            if (match == null || match.participants == null) continue;
            for (Participant participant : match.participants) {
                if (participant == null || !summoner.puuid().equals(participant.puuid) || participant.riotId == null || participant.riotId.isBlank()) continue;
                return participant.riotTag == null || participant.riotTag.isBlank()
                    ? participant.riotId
                    : participant.riotId + "#" + participant.riotTag;
            }
        }
        return getStoredRiotId(summoner);
    }

    private static String getStoredRiotId(Summoner summoner) {
        if (summoner.riotId() != null && !summoner.riotId().isBlank()) return summoner.riotId();
        String riotId = MongoDB.findSummonerName(summoner.puuid(), summoner.region());
        return riotId == null || riotId.isBlank() ? summoner.puuid() : riotId;
    }

    public static List<MessageTopLevelComponent> getOpggButtons(Summoner s, String user_id, LeagueMessageParameter parameter) {
        List<Match> matches = getOpggMatches(s, parameter);
        return getCanonicalOpggButtons(s, user_id, parameter, matches, getSelectedOpggMatch(s, parameter));
    }

    private static List<MessageTopLevelComponent> getCanonicalOpggButtons(
            Summoner summoner,
            String userId,
            LeagueMessageParameter parameter,
            List<Match> matches,
            Match selected) {
        int index = parameter.getOffset();
        GameQueueType queue = parameter.getQueueType();
        int order = 0;
        Button left = Button.primary(BUTTON_ID_PREFIX + "-leftpage-" + index, " ").withEmoji(CustomEmojiHandler.getRichEmoji("leftarrow"));
        if (index == 0) left = left.asDisabled();
        Button page = Button.primary(BUTTON_ID_PREFIX + "-index-" + index, "Match " + ((index / 5) + 1)).asDisabled();
        Button right = Button.primary(BUTTON_ID_PREFIX + "-rightpage-" + index, " ").withEmoji(CustomEmojiHandler.getRichEmoji("rightarrow"));

        List<MessageTopLevelComponent> buttons = new ArrayList<>(composeButtons(summoner, userId, parameter));
        StringSelectMenu menu = getCanonicalOpggMenu(summoner, matches, parameter);
        if (menu != null) {
            buttons.add(0, ActionRow.of(menu));
            order++;
        }

        if (parameter.getSelectedMatchId() != null) {
            StringSelectMenu selectedMenu = selected == null ? null : getCanonicalSelectedMatchMenu(selected);
            if (selectedMenu != null) buttons.add(1, ActionRow.of(selectedMenu));
        } else {
            buttons.add(order, LeagueMessageUtils.getOpggQueueTypeButtons(queue));
        }
        order++;
        buttons.add(order, ActionRow.of(left, page, right));
        return buttons;
    }

    private static StringSelectMenu getCanonicalSelectedMatchMenu(Match match) {
        ArrayList<SelectOption> options = new ArrayList<>();
        if (match.participants == null) return null;
        for (Participant participant : match.participants) {
            String name = participant.riotId == null || participant.riotId.isBlank()
                    ? participant.puuid
                    : participant.riotId + (participant.riotTag == null || participant.riotTag.isBlank() ? "" : "#" + participant.riotTag);
            if (participant.puuid == null || participant.puuid.isBlank() || match.leagueShard == null) continue;
            SelectOption option = SelectOption.of(name, participant.puuid + "#" + match.leagueShard.name());
            Emoji icon = ChampionUtils.getEmojiByChampion(participant.champion);
            options.add(icon == null ? option : option.withEmoji(icon));
        }
        return StringSelectMenu.create(LeagueMessage.BUTTON_ID_PREFIX + "-rankselect")
                .setPlaceholder("Select a summoner")
                .setMaxValues(1)
                .addOptions(options)
                .build();
    }

    public static EmbedBuilder getLivegameEmbed(Summoner summoner, LiveGame game, List<LiveGame.Participant> spectators) {
        String riotId = getStoredRiotId(summoner);
        String gameName = summoner.name().isBlank() ? riotId : summoner.name();
        if (game == null || game.notInGame() || spectators == null || spectators.isEmpty()) {
            EmbedBuilder empty = new EmbedBuilder();
            empty.setTitle(gameName + "'s Game");
            empty.setColor(Bot.getColor());
            empty.setThumbnail(LeagueHandler.getSummonerProfilePic(summoner));
            empty.setDescription("This user is not in a game.");
            return empty;
        }
        try {
            EmbedBuilder builder = new EmbedBuilder();
            builder.setAuthor(riotId, null, LeagueHandler.getSummonerProfilePic(summoner));
            builder.setDescription("Currently playing a **" + GameQueueTypeUtils.prettyName(game.queue()) + "** started <t:" + ((game.startedAt() / 1000)) + ":R>");
            builder.setColor(Bot.getColor());
            builder.setThumbnail(LeagueHandler.getSummonerProfilePic(summoner));

            switch (game.queue()) {
                case CHERRY:
                    String field1 = "";
                    String field2 = "";
                    int i = 0;

                    for (LiveGame.Participant participant : spectators) {
                        Summoner s = SummonerService.get(participant.puuid(), summoner.region());
                        String mastery = LeagueHandler.getMasteryByChamp(s, participant.championId());
                        String stats = LeagueHandler.getRankIcon(LeagueHandler.getRankEntry(s));
                        String sum = " **" + participant.riotId() + "**";

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

                    for (int championId : game.bans().getOrDefault(TeamType.BLUE, List.of()))
                        blueBans += ChampionUtils.getFormattedEmojiByChampion(championId) + " ";
                    for (int championId : game.bans().getOrDefault(TeamType.RED, List.of()))
                        redBans += ChampionUtils.getFormattedEmojiByChampion(championId) + " ";

                    for (LiveGame.Participant participant : spectators) {
                        String championIcon = ChampionUtils.getFormattedEmojiByChampion(participant.championId());

                        String stats = CustomEmojiHandler.getFormattedEmoji("unranked") + "\n`Unranked`";
                        LeagueEntry entry = LeagueHandler.getEntry(game.queue(), participant.puuid(), summoner.region());
                        if (entry != null) {
                            int wins = entry.getWins();
                            int losses = entry.getLosses();
                            double winrate = (Double.valueOf(wins) / Double.valueOf(wins + losses)) * 100;
                            stats = CustomEmojiHandler.getFormattedEmoji(entry.getTier()) + "\n`" + TierDivisionUtils.getFormattedRank(entry.getTierDivisionType(), false) + " " + String.valueOf(entry.getLeaguePoints()) + "LP \n" + wins + "W/" + losses + "L " + "(" + Math.ceil(winrate) + " WR%)`";
                            entryName = GameQueueTypeUtils.prettyName(entry.getQueueType());
                        }

                        String field = championIcon + "**" + participant.riotId() + "**" + stats + "\n";

                        if (participant.team() == TeamType.BLUE) blueSide += field;
                        else redSide += field;

                    }

                    builder.addField("Rank queue", "Showing ranks about " + entryName, false);

                    builder.addField("**BLUE SIDE**", "**Bans\n**" + blueBans + "\n\n**Picks**\n" + blueSide, true);
                    builder.addField("**RED SIDE**", "**Bans\n**" + redBans + "\n\n**Picks**\n" + redSide, true);
                    break;
            }

            builder.setFooter("For every gamemode would be use the SoloQ ranked data. Flex would be shown only if the game is a Flex game.");
            return builder;

        } catch (Exception e) {
            EmbedBuilder builder = new EmbedBuilder();
            builder.setTitle(gameName + "'s Game");
            builder.setColor(Bot.getColor());
            builder.setThumbnail(LeagueHandler.getSummonerProfilePic(summoner));
            builder.setDescription("This user is not in a game.");
            return builder;
        }
    }

    public static StringSelectMenu getLivegameMenu(Summoner summoner, List<LiveGame.Participant> spectators) {
        if (spectators == null || spectators.size() == 0) return null;

        ArrayList<SelectOption> options = new ArrayList<>();
        for (LiveGame.Participant participant : spectators) {
            if (participant.puuid() == null) continue;
            Emoji icon = ChampionUtils.getEmojiByChampion(participant.championId());
            options.add(SelectOption.of(participant.riotId(), participant.puuid() + "#" + summoner.region().name()).withEmoji(icon));
        }

        return StringSelectMenu.create("rank-select")
                .setPlaceholder("Select a summoner")
                .setMaxValues(1)
                .addOptions(options)
                .build();
    }

    private static MessageEmbed buildEmbedChampion(String userId, Summoner summoner, String puuid, LeagueMessageParameter parameter) {
        String riotId = getStoredRiotId(summoner);
        List<Match> matches = null;
        EmbedBuilder eb = new EmbedBuilder();
        if (parameter.isShowChampion()) eb.setThumbnail(ChampionUtils.getChampionProfilePic(parameter.getChampion().getId()));
        else eb.setThumbnail(LeagueHandler.getSummonerProfilePic(summoner));

        eb.setAuthor(riotId, null, LeagueHandler.getSummonerProfilePic(summoner));
        eb.setColor(Bot.getColor());
        switch (parameter.getMessageType()) {
            case OVERVIEW, MATCHUP, OVERVIEW_CHAMPIONS -> {
                ProfileStatistics statistics = profileStatistics(summoner, parameter);
                if (statistics == null) {
                    eb.setDescription("Statistics are being prepared for this filter.");
                } else {
                    switch (parameter.getMessageType()) {
                        case OVERVIEW -> eb = getGenericStats(eb, statistics, summoner, parameter);
                        case MATCHUP -> eb = getLegacyMatchups(eb, statistics, summoner, parameter);
                        case OVERVIEW_CHAMPIONS -> eb = getLegacyChampions(eb, statistics, summoner, parameter);
                        default -> { }
                    }
                }
            }
            case OVERVIEW_OPGG -> {
                Filter filter = parameter.toFilter();
                matches = MongoDB.getMatches(
                    puuid,
                    filter,
                    parameter.getMessageType().getPageItem(),
                    parameter.getOffset()
                );
                eb = getChampionOPGG(eb, matches, summoner, puuid, parameter);
            }
            default -> {
            }
        }
        return eb.build();
    }

    private static List<MessageTopLevelComponent> getChampionButtons(String userId, Summoner summoner, LeagueMessageParameter parameter) {
        StaticChampion champion = parameter.getChampion();
        
        Button left = Button.primary("lol-left", " ").withEmoji(CustomEmojiHandler.getRichEmoji("leftarrow"));
        Button right = Button.primary("lol-right", " ").withEmoji(CustomEmojiHandler.getRichEmoji("rightarrow"));

        Button center = Button.primary("lol-center-" + summoner.puuid() + "#" + summoner.region().name(),
            summoner.name().isBlank() ? getStoredRiotId(summoner) : summoner.name());
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
        Button champions = Button.primary("lol-type-" + LeagueMessageType.OVERVIEW_CHAMPIONS, "Champions");
        Button opgg = Button.primary("lol-type-" + LeagueMessageType.OVERVIEW_OPGG, "Opgg");

        switch (parameter.getMessageType()) {
            case OVERVIEW:
                generic = generic.withStyle(ButtonStyle.SUCCESS).asDisabled();
                break;
            case MATCHUP:
                matchups = matchups.withStyle(ButtonStyle.SUCCESS).asDisabled();
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

    private static ProfileStatistics profileStatistics(Summoner summoner, LeagueMessageParameter parameter) {
        Filter filter = parameter.toFilter();
        ProfileStatistics statistics = PROFILE_SERVICE.getStatistics(summoner.puuid(), summoner.region(), filter);
        if (statistics == null) {
            com.safjnest.lol.model.summoner.Summoner saved = SummonerService.find(summoner.puuid(), summoner.region());
            if (saved != null) ComputeScheduler.startProfileStatistics(saved, filter);
        }
        return statistics;
    }

    private static EmbedBuilder addLegacyProfileStats(
        EmbedBuilder builder,
        ProfileStatistics statistics,
        Summoner summoner,
        LeagueMessageParameter parameter
    ) {
        if (statistics.total == null || statistics.total.games == 0) return builder;

        String laneString = formatLegacyLaneStats(statistics.laneStats);
        if (parameter.getQueueType() == null) {
            builder.addField("Games", formatLegacyQueueStats(statistics.queueStats), true);
            builder.addField("Roles", laneString, true);
        } else {
            builder.addField("Games", laneString, false);
        }

        List<Stats<Integer>> champions = sortedStats(statistics.championStats);
        Map<Integer, Mastery> masteries = LeagueHandler.getMastery(summoner);
        StringBuilder championString = new StringBuilder();
        for (int index = 0; index < Math.min(6, champions.size()); index++) {
            championString.append(formatLegacyChampionStat(champions.get(index), masteries.get(champions.get(index).reference)));
        }
        builder.addField("Champions", championString.toString(), false);
        return builder;
    }

    private static String formatLegacyLaneStats(List<Stats<LaneType>> values) {
        StringBuilder result = new StringBuilder();
        for (Stats<LaneType> stat : sortedStats(values)) {
            if (stat.reference == null || stat.reference == LaneType.NONE) continue;
            result.append(LaneTypeUtils.getLaneTypeEmoji(stat.reference)).append(" ")
                .append(LaneTypeUtils.getPrettyName(stat.reference)).append(" ")
                .append(stat.games).append(" games\n`(")
                .append(stat.wins).append("W/").append(stat.losses()).append("L) - ")
                .append(String.format("%.2f", stat.winrate)).append("% WR`\n");
        }
        return result.toString();
    }

    private static String formatLegacyQueueStats(List<Stats<GameQueueType>> values) {
        StringBuilder result = new StringBuilder();
        List<Stats<GameQueueType>> sorted = sortedStats(values);
        long otherWins = 0;
        long otherLosses = 0;
        for (int index = 0; index < sorted.size(); index++) {
            Stats<GameQueueType> stat = sorted.get(index);
            if (index >= 4) {
                otherWins += stat.wins;
                otherLosses += stat.losses();
                continue;
            }
            result.append(GameQueueTypeUtils.getMapEmoji(stat.reference)).append(" ")
                .append(GameQueueTypeUtils.prettyName(stat.reference)).append(" ")
                .append(stat.games).append(" games\n`(")
                .append(stat.wins).append("W/").append(stat.losses()).append("L) - ")
                .append(String.format("%.2f", stat.winrate)).append("% WR`\n");
        }
        if (otherWins > 0 || otherLosses > 0) {
            long otherGames = otherWins + otherLosses;
            result.append(CustomEmojiHandler.getFormattedEmoji("special_mode"))
                .append("Others ").append(otherGames).append(" games\n`(")
                .append(otherWins).append("W/").append(otherLosses).append("L) - ")
                .append(String.format("%.2f", (double) otherWins * 100 / otherGames)).append("% WR`\n");
        }
        return result.toString();
    }

    private static <T> List<Stats<T>> sortedStats(List<Stats<T>> values) {
        List<Stats<T>> sorted = values == null ? new ArrayList<>() : new ArrayList<>(values);
        sorted.sort((left, right) -> {
            int games = Long.compare(right.games, left.games);
            return games != 0 ? games : Double.compare(right.winrate, left.winrate);
        });
        return sorted;
    }

    private static String formatLegacyChampionStat(Stats<Integer> stat, Mastery mastery) {
        StaticChampion champion = ChampionUtils.getChampion(stat.reference);
        if (champion == null) return stat.reference + "\n";
        int level = mastery == null ? 0 : Math.min(10, mastery.level());
        return CustomEmojiHandler.getFormattedEmoji("mastery" + level) + " "
            + CustomEmojiHandler.getFormattedEmoji(champion.getName()) + " **["
            + (mastery == null ? 0 : mastery.level()) + "]** " + champion.getName() + ": "
            + stat.games + " games (" + stat.wins + "W/" + stat.losses() + "L) | " + stat.lpGain + "LP\n"
            + "`Avg. KDA " + String.format("%.2f", stat.avgKills) + "/"
            + String.format("%.2f", stat.avgDeaths) + "/" + String.format("%.2f", stat.avgAssists) + "`\n";
    }

    private static EmbedBuilder getGenericStats(
        EmbedBuilder eb,
        ProfileStatistics statistics,
        Summoner summoner,
        LeagueMessageParameter parameter
    ) {
        Stats<Void> total = statistics.total;
        if (total == null || total.games == 0) {
            eb.setDescription("Not enough games");
            eb.addField("Last update", formatLastUpdate(statistics.lastUpdate), false);
            return eb;
        }

        boolean arena = GameQueueTypeUtils.isCherry(parameter.getQueueType());
        String championString = " with " + statistics.championStats.size() + " different champions";
        if (parameter.isShowChampion()) {
            StaticChampion champion = ChampionUtils.getChampion(parameter.getChampionId());
            championString = champion == null ? " with " + parameter.getChampionId()
                : " with " + CustomEmojiHandler.getFormattedEmoji(champion.getName()) + " " + champion.getName();
        }
        eb.setDescription(
            "Summoner has played **" + total.games + "** games" + championString
            + "\nA total of **" + SafJNest.getFormattedDurationWithUnits(total.playtime) + "**\n"
            + "Oldest game: <t:" + totalOldest(statistics) / 1000 + ":R>\n"
            + "Newest game: <t:" + totalNewest(statistics) / 1000 + ":R>"
        );

        if (!arena) {
            eb.addField("Games", formatLegacyQueueStats(statistics.queueStats), true);
            eb.addField("Roles", formatLegacyLaneStats(statistics.laneStats), true);
        }

        if (!parameter.isShowChampion()) {
            List<Stats<Integer>> champions = sortedStats(statistics.championStats);
            Map<Integer, Mastery> masteries = LeagueHandler.getMastery(summoner);
            String championStats = champions.stream()
                .limit(6)
                .map(stat -> formatLegacyChampionStat(stat, masteries.get(stat.reference)))
                .collect(Collectors.joining(""));
            eb.addField("Champions", championStats, false);
        }

        String kda = String.format("%.2f", total.avgKills) + "/" + String.format("%.2f", total.avgDeaths)
            + "/" + String.format("%.2f", total.avgAssists);
        String visionScore = String.format("%.2f", total.avgVision) + " VS ("
            + String.format("%.2f", total.avgWard) + " placed / " + String.format("%.2f", total.avgWardKilled) + " destroyed)";
        double csPerMinute = total.playtime == 0 ? 0 : total.cs * 60000.0 / total.playtime;
        String cs = String.format("%.2f", total.avgCs) + " (" + String.format("%.2f", csPerMinute) + " / min)";
        String damage = String.format("%.2f", total.avgDamage) + " to champ / "
            + String.format("%.2f", total.avgDamageBuilding) + " to buildings";
        String arenaPlacement = "";
        if (arena) {
            arenaPlacement = "1. " + total.arenaFirst + " times\n"
                + "2. " + total.arenaSecond + " times\n"
                + "3. " + total.arenaThird + " times\n"
                + "avg. " + String.format("%.2f", total.avgArenaPlacement) + " placement";
        }

        StringBuilder streak = new StringBuilder();
        if (total.pentas > 0) streak.append("Pentakills: ").append(total.pentas).append("\n");
        if (total.quadruples > 0) streak.append("Quadrakills: ").append(total.quadruples).append("\n");
        if (total.triples > 0) streak.append("Triplakills: ").append(total.triples).append("\n");
        if (total.doubles > 0) streak.append("Doublekills: ").append(total.doubles).append("\n");
        String streakString = streak.toString().trim();
        String performance = (arena ? "**Placement**\n`" + arenaPlacement + "`\n" : "")
            + "**KDA**\n`" + kda + " (" + String.format("%.2f", value(total.avgKillParticipation)) + "% kp & "
            + String.format("%.2f", value(total.avgDeathShare)) + "% dp)\n"
            + (!streakString.isEmpty() ? streakString + "`\n" : "`")
            + (!arena ? "**Vision Score**\n`" + visionScore + "`\n**CS**\n`" + cs + "`\n" : "")
            + "**Damage**\n`" + damage + "`\n"
            + (!arena ? "**Gold Earned**\n`" + String.format("%.2f", total.avgGold) + "`\n" : "");
        eb.addField("Average Performance", performance, false);
        eb.addField("Spell Performance", legacyAbilityStats(total), true);
        eb.addField(" ", legacySpellStats(statistics.spellOne, "d_", "Spell 1"), true);
        eb.addField(" ", legacySpellStats(statistics.spellTwo, "f_", "Spell 2"), true);
        addLegacyPingFields(eb, statistics.pings);
        eb.addField("Last update", formatLastUpdate(statistics.lastUpdate), false);
        return eb;
    }

    private static long totalOldest(ProfileStatistics statistics) {
        return statistics.oldestMatchAt == 0 ? System.currentTimeMillis() : statistics.oldestMatchAt;
    }

    private static long totalNewest(ProfileStatistics statistics) {
        return statistics.newestMatchAt == 0 ? System.currentTimeMillis() : statistics.newestMatchAt;
    }

    private static String legacyAbilityStats(Stats<Void> total) {
        return CustomEmojiHandler.getFormattedEmoji("q_") + " Ability 1\n`" + total.q + " times`\n"
            + CustomEmojiHandler.getFormattedEmoji("w_") + " Ability 2\n`" + total.w + " times`\n"
            + CustomEmojiHandler.getFormattedEmoji("e_") + " Ability 3\n`" + total.e + " times`\n"
            + CustomEmojiHandler.getFormattedEmoji("r_") + " Ultimate\n`" + total.r + " times`\n";
    }

    private static String legacySpellStats(Map<Integer, Long> values, String emoji, String name) {
        List<Integer> top = values == null ? new ArrayList<>() : values.entrySet().stream()
            .filter(entry -> entry.getKey() != 0)
            .sorted(Map.Entry.<Integer, Long>comparingByValue().reversed())
            .limit(3)
            .map(Map.Entry::getKey)
            .toList();
        StringBuilder result = new StringBuilder(CustomEmojiHandler.getFormattedEmoji(emoji) + " " + name + "\n`");
        long total = values == null ? 0 : values.values().stream().mapToLong(Long::longValue).sum();
        result.append(total).append(" times`\n");
        for (int id : top) result.append(CustomEmojiHandler.getFormattedEmoji(id + "_")).append(" ")
            .append(LeagueHandler.getSpellName(id)).append("\n`").append(values.get(id)).append(" times`\n");
        return result.toString();
    }

    private static void addLegacyPingFields(EmbedBuilder eb, Map<String, Long> values) {
        List<Map.Entry<String, Long>> sorted = (values == null ? Map.<String, Long>of() : values).entrySet().stream()
            .filter(entry -> !entry.getKey().equals("basic"))
            .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
            .limit(9)
            .toList();
        StringBuilder[] columns = {new StringBuilder(), new StringBuilder(), new StringBuilder()};
        for (int index = 0; index < sorted.size(); index++) {
            Map.Entry<String, Long> entry = sorted.get(index);
            String pingName = "command".equals(entry.getKey()) ? "Generic Ping" : Arrays.stream(entry.getKey().replace("_", " ").split(" "))
                .map(word -> word.isEmpty() ? "" : Character.toUpperCase(word.charAt(0)) + word.substring(1).toLowerCase())
                .collect(Collectors.joining(" "));
            columns[index / 3].append(CustomEmojiHandler.getFormattedEmoji(entry.getKey() + "_ping"))
                .append(" ").append(pingName).append("\n`").append(entry.getValue()).append(" total`\n");
        }
        eb.addField("Pings Usage", columns[0].toString(), true);
        eb.addField(" ", columns[1].toString(), true);
        eb.addField(" ", columns[2].toString(), true);
    }

    private static EmbedBuilder getLegacyMatchups(
        EmbedBuilder eb,
        ProfileStatistics statistics,
        Summoner summoner,
        LeagueMessageParameter parameter
    ) {
        if (statistics.total == null || statistics.total.games == 0) {
            eb.setDescription("Not enough games");
            return eb;
        }
        eb.setDescription("Summoner has played **" + statistics.total.games + "** games with "
            + statistics.championStats.size() + " different champions");
        ProfileMatchups matchups = PROFILE_SERVICE.getMatchups(summoner.region(), summoner.puuid(), parameter.toFilter());
        if (matchups == null) ComputeScheduler.startProfileMatchups(summoner.puuid(), summoner.region(), parameter.toFilter());
        else eb = LeagueMessageUtils.buildMatchups("matchups", eb, toLegacyMatchups(matchups.aggregateMatchups()));
        return eb;
    }

    private static EmbedBuilder getLegacyChampions(
        EmbedBuilder eb,
        ProfileStatistics statistics,
        Summoner summoner,
        LeagueMessageParameter parameter
    ) {
        if (statistics.total == null || statistics.total.games == 0) {
            eb.setDescription("Not enough games");
            return eb;
        }
        List<Stats<Integer>> champions = sortedStats(statistics.championStats);
        int offset = Math.min(Math.max(0, parameter.getOffset()), champions.size());
        Map<Integer, Mastery> masteries = LeagueHandler.getMastery(summoner);
        String championString = champions.stream()
            .skip(offset)
            .limit(10)
            .map(stat -> formatLegacyChampionStat(stat, masteries.get(stat.reference)).trim())
            .collect(Collectors.joining("\n"));
        eb.setDescription("Summoner has played **" + statistics.total.games + "** games with "
            + champions.size() + " different champions\n\n" + championString);
        int pages = (int) Math.ceil((double) champions.size() / 10);
        eb.setFooter("Page " + (parameter.getOffset() / 10 + 1) + " / " + pages);
        return eb;
    }

    private static HashMap<Integer, long[]> toLegacyMatchups(Map<Integer, ? extends Stats<?>> values) {
        HashMap<Integer, long[]> result = new HashMap<>();
        if (values == null) return result;
        for (Map.Entry<Integer, ? extends Stats<?>> stat : values.entrySet()) result.put(stat.getKey(), new long[] {stat.getValue().wins, stat.getValue().losses()});
        return result;
    }

    private static String formatLastUpdate(long value) {
        if (value <= 0) return "not available";
        long timestamp = value / 1000;
        return "<t:" + timestamp + ":F> (<t:" + timestamp + ":R>)";
    }

    private static double value(Double value) {
        return value == null ? 0 : value;
    }

    private static EmbedBuilder getChampionOPGG(EmbedBuilder eb, List<Match> matches, Summoner s, String puuid, LeagueMessageParameter parameter) {
        List<QueryRecord> queryResult = MatchService.getSummonerData(s.puuid(), s.region());
        for (Match match : matches) 
            eb = getOpggEmbedMatchPreview(eb, match, s, queryResult);
        
        int totalPages = MongoDB.countMatches(puuid, parameter.toFilter());
        int pages = (int) Math.ceil((double) totalPages / 5);
        int currentPage = (parameter.getOffset() / 5) + 1;
        eb.setFooter("Page " + currentPage + " / " + pages);
        eb.setThumbnail(null);
        return eb;
    }
}
