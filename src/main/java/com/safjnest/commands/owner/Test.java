package com.safjnest.commands.owner;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.App;
import com.safjnest.core.cache.managers.GuildCache;
import com.safjnest.core.cache.managers.UserCache;
import com.safjnest.lol.model.Filter;
import com.safjnest.lol.model.match.Match;
import com.safjnest.lol.queue.QueueHandler;
import com.safjnest.lol.queue.scheduler.ComputeScheduler;
import com.safjnest.lol.queue.scheduler.DatabaseWorkerType;
import com.safjnest.lol.queue.scheduler.RiotScheduler;
import com.safjnest.lol.queue.scheduler.SyncScheduler;
import com.safjnest.lol.service.ChampionService;
import com.safjnest.lol.service.CompetitiveService;
import com.safjnest.lol.service.LeaderboardService;
import com.safjnest.lol.service.MatchService;
import com.safjnest.lol.service.ProfileRecordService;
import com.safjnest.lol.service.ProfileService;
import com.safjnest.lol.service.SummonerService;
import com.safjnest.lol.tracker.Tracker;
import com.safjnest.lol.tracker.TrackerScheduler;
import com.safjnest.lol.utils.LeagueShardUtils;
import com.safjnest.lol.utils.MatchMemoryUtils;
import com.safjnest.model.guild.BlacklistData;
import com.safjnest.model.guild.ChannelData;
import com.safjnest.model.guild.alert.AlertData;
import com.safjnest.model.guild.alert.AlertKey;
import com.safjnest.nosql.MongoMigration;
import com.safjnest.nosql.MongoDB;
import com.safjnest.utils.BotCommand;
import com.safjnest.utils.CommandsLoader;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.pojo.lol.match.v5.LOLTimeline;
import org.json.JSONObject;

public class Test extends Command {

    private static final int TIMELINE_NULL_STOP_THRESHOLD = 10;
    private static final List<String> REGENERATION_OPERATIONS = List.of(
        "profiles", "competitive", "aggregates", "records", "mastery-records", "champions", "indexables", "all"
    );
    private static final List<String> AUDIT_OPERATIONS = List.of("mastery-records");
    private static final List<String> MIGRATION_OPERATIONS = List.of(
        "all", "tracked", "ranks", "fix-tracked", "rankprogress [runId]"
    );

    public Test() {
        this.name = this.getClass().getSimpleName().toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(this.name);
        this.aliases = commandData.getAliases();
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        this.arguments = commandData.getArguments();
        this.ownerCommand = true;
        this.hidden = true;
        commandData.setThings(this);
    }

    @Override
    protected void execute(CommandEvent event) {
        String[] arguments = event.getArgs().trim().split("\\s+", 2);
        String operation = arguments.length == 0 ? "" : arguments[0].toLowerCase();
        switch (operation) {
            case "list" -> event.reply("gc | tracking | log | ranking | regenerate <"
                + String.join("|", REGENERATION_OPERATIONS) + "> | 13 | 14 | getblacklist | getserver | queue"
                + " | pushsamplegame [GameQueueType] | pushsamplegamecherry | pushsamplegamearam | pushhighelo"
                + " | retrieveallgames <summoner> | retrieveallgamesfast <summoner> | getrank | getallrank | highstats"
                + " | audit <" + String.join("|", AUDIT_OPERATIONS) + "> | migrate [" + String.join(" | ", MIGRATION_OPERATIONS)
                + "] | fix-timeline <puuid>");
            case "gc" -> {
                System.gc();
                event.reply("Garbage collection requested.");
            }
            case "tracking" -> {
                boolean enabled = App.toggleTracking();
                if (enabled) TrackerScheduler.scheduleIfEnabled();
                event.reply("Tracker scheduling " + (enabled ? "enabled" : "disabled") + ".");
            }
            case "log" -> event.reply("Riot scheduler logging "
                + (RiotScheduler.toggleLogging() ? "enabled" : "disabled") + ".");
            case "ranking" -> replyRankingStatus(event);
            case "13" -> printGuildCache(event);
            case "14" -> warmGuildCaches(event);
            case "getblacklist" -> System.out.println(GuildCache.getGuildOrPut(event.getGuild().getId()).getBlacklistData());
            case "getserver" -> event.reply("```json\\n"
                + new JSONObject(GuildCache.getGuildOrPut(event.getGuild().getId()).getChannels()) + "```");
            case "queue" -> event.reply("Queued jobs=" + QueueHandler.snapshot().size() + ".");
            case "pushsamplegame" -> {
                GameQueueType queue = null;
                if (arguments.length > 1 && !arguments[1].isBlank()) {
                    try {
                        queue = GameQueueType.valueOf(arguments[1].trim().toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException exception) {
                        event.reply("Usage: !test pushsamplegame [GameQueueType].");
                        return;
                    }
                }
                queueSampleGames(event, queue);
            }
            case "pushsamplegamecherry" -> queueSampleGames(event, GameQueueType.CHERRY);
            case "pushsamplegamearam" -> queueSampleGames(event, GameQueueType.ARAM);
            case "pushhighelo", "getrank" -> queueHighElo(event);
            case "getallrank" -> queueAllRanks(event);
            case "retrieveallgames" -> queueMatchHistory(event, arguments, null);
            case "retrieveallgamesfast" -> queueMatchHistory(event, arguments, GameQueueType.values());
            case "highstats" -> queueHighEloStatistics(event);
            case "regenerate", "regen" -> queueRegeneration(event, arguments);
            case "audit" -> queueAudit(event, arguments);
            case "migrate" -> queueMigration(event, arguments);
            case "fix-timeline" -> queueTimelineRepair(event, arguments);
            default -> event.reply("Usage: !test regenerate <"
                + String.join("|", REGENERATION_OPERATIONS) + "> | !test migrate [" + String.join("|", MIGRATION_OPERATIONS)
                + "] | !test fix-timeline <puuid>.");
        }
    }

    // ============================================================================

    private static void queueRegeneration(CommandEvent event, String[] arguments) {
        String operation = arguments.length < 2 ? "" : arguments[1].trim().toLowerCase();
        if (!REGENERATION_OPERATIONS.contains(operation)) {
            event.reply("Usage: !test regenerate <" + String.join("|", REGENERATION_OPERATIONS) + ">.");
            return;
        }
        QueueHandler.background(ComputeScheduler.class, DatabaseWorkerType.MONGO,
            "owner-regenerate:" + operation, "owner regenerate " + operation, job -> {
                regenerate(operation);
                return null;
            });
        event.reply("Regeneration " + operation + " queued.");
    }

    private static void queueMigration(CommandEvent event, String[] arguments) {
        String migrationInput = arguments.length < 2 ? "" : arguments[1].trim();
        if (migrationInput.isBlank()) {
            queueMigrationOperation(event, "all", "Full Mongo migration", MongoMigration::migrateAll);
            return;
        }
        String[] migrationArguments = migrationInput.split("\\s+", 2);
        String migration = migrationArguments[0].toLowerCase();
        switch (migration) {
            case "all" -> queueMigrationOperation(event, "all", "Full Mongo migration", MongoMigration::migrateAll);
            case "tracked" -> queueMigrationOperation(event, "tracked", "Tracked RankProgress recovery", MongoMigration::migrateTrackedRankProgress);
            case "ranks" -> queueMigrationOperation(event, "ranks", "Rank migration", MongoMigration::migrateRanks);
            case "fix-tracked" -> queueMigrationOperation(event, "fix-tracked", "Tracked RankProgress rebuild", MongoMigration::rebuildTrackedRankProgress);
            case "rankprogress" -> queueRankProgressMigration(event, migrationArguments);
            default -> event.reply("Usage: !test migrate [" + String.join("|", MIGRATION_OPERATIONS) + "].");
        }
    }

    private static void queueAudit(CommandEvent event, String[] arguments) {
        String operation = arguments.length < 2 ? "" : arguments[1].trim().toLowerCase();
        if (!AUDIT_OPERATIONS.contains(operation)) {
            event.reply("Usage: !test audit <" + String.join("|", AUDIT_OPERATIONS) + ">.");
            return;
        }
        QueueHandler.background(ComputeScheduler.class, DatabaseWorkerType.MONGO,
            "owner-audit:" + operation, "owner audit " + operation, job -> {
                System.out.println("[Mastery records audit] " + MongoDB.masteryRecordsSpaceAudit().toJson());
                return null;
            });
        event.reply("Audit " + operation + " queued.");
    }

    private static void queueMigrationOperation(CommandEvent event, String operation, String label, Runnable migration) {
        QueueHandler.background(ComputeScheduler.class, DatabaseWorkerType.MONGO,
            "owner-migration:" + operation, "owner migration " + operation, job -> {
                migration.run();
                return null;
            });
        event.reply(label + " queued.");
    }

    private static void queueRankProgressMigration(CommandEvent event, String[] arguments) {
        String runId = arguments.length < 2 || arguments[1].isBlank() ? "rankprogress-v1" : arguments[1].trim();
        QueueHandler.background(ComputeScheduler.class, DatabaseWorkerType.MONGO,
            "owner-migration:rankprogress:" + runId, "owner RankProgress migration " + runId, job -> {
                MongoMigration.migrateRankProgress(new MongoMigration.Options(false, 500_000, runId, true, 0));
                return null;
            });
        event.reply("RankProgress migration queued: run=" + runId + ".");
    }

    private static void printGuildCache(CommandEvent event) {
        HashMap<AlertKey<?>, AlertData> alerts = GuildCache.getGuildOrPut(event.getGuild().getId()).getAlerts();
        event.reply("```json\\n" + GuildCache.getGuildOrPut(event.getGuild().getId()) + "```");
        event.reply("```json\\n" + new JSONObject(alerts) + "```");
        BlacklistData blacklist = GuildCache.getGuildOrPut(event.getGuild().getId()).getBlacklistData();
        event.reply("```json\\n" + blacklist + "```");
        HashMap<String, ChannelData> channels = GuildCache.getGuildOrPut(event.getGuild().getId()).getChannels();
        event.reply("```json\\n" + new JSONObject(channels) + "```");
        event.reply("```json\\n" + new JSONObject(GuildCache.getGuildOrPut(event.getGuild().getId()).getMembers()) + "```");
        event.reply("```json\\n" + new JSONObject(GuildCache.getGuildOrPut(event.getGuild().getId()).getActionsWithId()) + "```");
    }

    private static void warmGuildCaches(CommandEvent event) {
        for (Guild guild : event.getJDA().getGuilds()) {
            GuildCache.getGuildOrPut(guild.getId()).getAlerts();
            GuildCache.getGuildOrPut(guild.getId()).getBlacklistData();
            for (GuildChannel channel : guild.getChannels())
                GuildCache.getGuildOrPut(guild.getId()).getChannelData(channel.getId());
            for (Member member : guild.getMembers()) {
                GuildCache.getGuildOrPut(guild.getId()).getMemberData(member.getId());
                UserCache.getUser(member.getId());
            }
        }
        event.reply("Done");
    }

    private static void queueSampleGames(CommandEvent event, GameQueueType queue) {
        String queueName = queue == null ? "ALL" : queue.name();
        QueueHandler.background(SyncScheduler.class, null, "owner-sample-games:" + queueName,
            "owner sample games " + queueName, job -> {
                TrackerScheduler.retrieveSampleGames(queue);
                return null;
            });
        event.reply("Sample game retrieval queued: " + (queue == null ? "all queues" : queue.name()) + ".");
    }

    private static void queueTimelineRepair(CommandEvent event, String[] arguments) {
        if (arguments.length < 2 || arguments[1].isBlank()) {
            event.reply("Usage: !test fix-timeline <puuid>.");
            return;
        }
        String puuid = arguments[1].trim();
        QueueHandler.background(SyncScheduler.class, null, "owner-fix-timeline:" + puuid,
            "owner fix timeline " + puuid, job -> {
                Filter filter = Filter.summoner(0, 0);
                int total = MongoDB.countMatches(puuid, filter);
                int[] counts = new int[3];
                System.out.println("[Fix timelines] puuid=" + puuid + " total=" + total);
                job.setProgressTotal(total);
                job.phase("TIMELINES");
                int[] step = {0};
                int[] missingTimelineStreak = {0};
                MongoDB.forEachProfileStatisticsMatchWhile(puuid, null, filter, 0, 0, true, match -> {
                    int current = ++step[0];
                    String gameId = match.gameId;
                    String itemId = gameId == null || gameId.isBlank() ? "missing-game-id:" + current : gameId;
                    job.trackItem(itemId);
                    job.currentItem(current + "/" + total + " Fetching timeline " + itemId);
                    System.out.println("[Fix timelines] " + current + "/" + total + " match=" + itemId);
                    if (gameId == null || gameId.isBlank()) {
                        counts[2]++;
                        job.failed(itemId);
                        missingTimelineStreak[0] = 0;
                        MatchMemoryUtils.release(match);
                        return true;
                    }
                    LOLTimeline timeline = null;
                    try {
                        if (match.leagueShard == null) {
                            counts[1]++;
                            job.missing(itemId);
                            missingTimelineStreak[0] = 0;
                            return true;
                        }
                        timeline = MatchService.getTimeline(gameId, match.leagueShard);
                        if (timeline == null) {
                            counts[1]++;
                            job.missing(itemId);
                            int consecutiveMissing = ++missingTimelineStreak[0];
                            boolean stop = consecutiveMissing >= TIMELINE_NULL_STOP_THRESHOLD;
                            job.currentItem(current + "/" + total + (stop
                                ? " Stopping after " + consecutiveMissing + " consecutive missing timelines "
                                : " Timeline unavailable ") + itemId);
                            if (stop) System.out.println("[Fix timelines] stopping after " + consecutiveMissing
                                + " consecutive missing timelines at match=" + gameId);
                            return !stop;
                        }
                        missingTimelineStreak[0] = 0;
                        Map<String, Object> refreshedEvents = Tracker.rebuildTimelineEvents(timeline);
                        if (refreshedEvents == null || refreshedEvents.isEmpty()) {
                            counts[2]++;
                            job.failed(itemId);
                            return true;
                        }
                        MongoDB.updateMatchEvents(gameId, refreshedEvents);
                        counts[0]++;
                        job.done(itemId);
                    } catch (Exception exception) {
                        counts[2]++;
                        job.failed(itemId);
                        missingTimelineStreak[0] = 0;
                        System.err.println("[Fix timelines] failed match=" + gameId + " error=" + exception.getMessage());
                    } finally {
                        MatchService.clearTimelineCache(gameId, match.leagueShard);
                        MatchMemoryUtils.release(timeline);
                        MatchMemoryUtils.release(match);
                    }
                    return true;
                });
                System.out.println("[Fix timelines] puuid=" + puuid + " total=" + total
                    + " completed=" + (counts[0] + counts[1] + counts[2])
                    + " updated=" + counts[0] + " missing=" + counts[1] + " failed=" + counts[2]);
                return null;
            }).whenComplete((ignored, failure) -> {
                if (failure == null) System.out.println("[Fix timelines] job completed puuid=" + puuid);
                else System.err.println("[Fix timelines] job failed puuid=" + puuid + " error=" + failure.getMessage());
            });
        event.reply("Timeline repair queued for PUUID " + puuid + ".");
    }

    private static void queueHighElo(CommandEvent event) {
        QueueHandler.background(SyncScheduler.class, null, "owner-high-elo", "owner high elo retrieval", job -> {
            TrackerScheduler.retrieveHighEloEntries();
            return null;
        });
        event.reply("High-elo retrieval queued.");
    }

    private static void queueAllRanks(CommandEvent event) {
        QueueHandler.background(SyncScheduler.class, null, "owner-all-ranks", "owner all rank retrieval", job -> {
            TrackerScheduler.retrieveAllEntries();
            return null;
        });
        event.reply("All-rank retrieval queued.");
    }

    private static void queueMatchHistory(CommandEvent event, String[] arguments, GameQueueType[] queues) {
        if (arguments.length < 2 || arguments[1].isBlank()) {
            event.reply("Usage: !test " + (queues == null ? "retrieveallgames" : "retrieveallgamesfast") + " <summoner>.");
            return;
        }
        String summonerName = arguments[1].trim();
        LeagueShard shard = GuildCache.getGuild(event.getGuild()).getLeagueShard(event.getChannel().getId());
        QueueHandler.background(SyncScheduler.class, shard, "owner-match-history:" + shard.name() + ":" + summonerName,
            "owner match history " + summonerName, job -> {
                no.stelar7.api.r4j.pojo.lol.summoner.Summoner summoner = SummonerService.getRiotSummoner(summonerName, shard);
                if (queues == null) MatchService.importHistory(summoner, null);
                else for (GameQueueType queue : queues) MatchService.importHistory(summoner, queue);
                return null;
            });
        event.reply("Match history retrieval queued for " + summonerName + ".");
    }

    private static void queueHighEloStatistics(CommandEvent event) {
        QueueHandler.background(ComputeScheduler.class, DatabaseWorkerType.MONGO, "owner-high-elo-statistics",
            "owner high elo statistics", job -> {
                new LeaderboardService().rebuildHighEloAndTrackedProfileStatistics();
                return null;
            });
        event.reply("High-elo statistics rebuild queued.");
    }

    private static void regenerate(String operation) {
        switch (operation) {
            case "profiles" -> regenerateProfiles();
            case "competitive" -> regenerateCompetitive();
            case "aggregates" -> regenerateAggregates();
            case "records" -> regenerateRecords();
            case "mastery-records" -> regenerateMasteryRecords();
            case "champions" -> regenerateChampions();
            case "indexables" -> regenerateIndexables();
            case "all" -> {
                regenerateProfiles();
                regenerateCompetitive();
                regenerateAggregates();
                regenerateRecords();
                regenerateMasteryRecords();
                regenerateChampions();
                regenerateIndexables();
            }
            default -> throw new IllegalArgumentException("Unsupported regeneration operation: " + operation);
        }
    }

    private static void regenerateProfiles() {
        ProfileService profileService = new ProfileService();
        int refreshed = 0;
        for (LeagueShard shard : LeagueShardUtils.getActives()) {
            int result = profileService.refreshAll(shard, true);
            if (result > 0) refreshed += result;
        }
        System.out.println("[Regenerate profiles] refreshed=" + refreshed);
    }

    private static void regenerateCompetitive() {
        var competitive = CompetitiveService.rebuild();
        System.out.println("[Regenerate competitive] entries=" + competitive.entries()
            + " removed=" + competitive.removed());
    }

    private static void regenerateAggregates() {
        var aggregates = LeaderboardService.rebuildAllAggregates();
        System.out.println("[Regenerate leaderboard aggregates] total=" + aggregates.total());
    }

    private static void regenerateRecords() {
        ProfileService profileService = new ProfileService();
        int refreshed = 0;
        for (LeagueShard shard : LeagueShardUtils.getActives())
            refreshed += profileService.refreshAllRecords(shard);
        System.out.println("[Regenerate records] refreshed=" + refreshed);
    }

    private static void regenerateMasteryRecords() {
        ProfileRecordService profileRecordService = new ProfileRecordService();
        int refreshed = 0;
        for (LeagueShard shard : LeagueShardUtils.getActives()) refreshed += profileRecordService.regenerateMasteries(shard);
        System.out.println("[Regenerate mastery records] refreshed=" + refreshed);
    }

    private static void regenerateChampions() {
        new ChampionService().refresh();
        System.out.println("[Regenerate champions] completed.");
    }

    private static void regenerateIndexables() {
        int champions = new ChampionService().refreshIndexables().size();
        int profiles = new ProfileService().refreshIndexables().size();
        System.out.println("[Regenerate indexables] champions=" + champions + " profiles=" + profiles);
    }

    private static void replyRankingStatus(CommandEvent event) {
        LeaderboardService.IndexStatus leaderboard = LeaderboardService.indexStatus();
        ProfileRecordService.IndexStatus records = ProfileRecordService.indexStatus();
        event.reply("Leaderboard segments=" + leaderboard.segments() + " memoryBytes=" + leaderboard.memoryBytes()
            + " | record segments=" + records.segments() + " memoryBytes=" + records.memoryBytes());
    }

}
