package com.safjnest.commands.owner;

import java.util.List;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.core.Bot;
import com.safjnest.lol.model.status.BotStatus;
import com.safjnest.lol.model.status.QueueWorkerStatus;
import com.safjnest.lol.model.status.TrackerMetrics;
import com.safjnest.status.StatusService;
import com.safjnest.utils.BotCommand;
import com.safjnest.utils.CommandsLoader;

import net.dv8tion.jda.api.EmbedBuilder;

public class TrackerStatus extends Command {

    private static final int MAX_QUEUE_DESCRIPTION_LENGTH = 3600;

    public TrackerStatus() {
        this.name = "tracker";

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
        BotStatus status = new StatusService().current();
        TrackerMetrics tracker = status.tracker();
        List<QueueWorkerStatus> workers = status.workers().workers();

        event.getChannel().sendMessageEmbeds(
            schedulerEmbed(tracker).build(),
            workerEmbed(workers.get(0)).build(),
            workerEmbed(workers.get(1)).build()
        ).queue();
    }

    private static EmbedBuilder schedulerEmbed(TrackerMetrics status) {
        EmbedBuilder embed = baseEmbed("Tracker status");
        embed.addField("Scheduler", status.scheduler().toUpperCase(), true);
        embed.addField("Tracking", state(status.tracking().state()), true);
        embed.addField("High elo rank", state(status.highElo().state()), true);
        embed.addField("Game analysis", state(status.gameAnalysis().state()), true);
        embed.addField("Next runs",
            "Tracking: " + timestamp(status.tracking().nextRunAt())
                + "\nHigh elo rank: " + timestamp(status.highElo().nextRunAt())
                + "\nGame queue: " + timestamp(status.gameAnalysis().nextRunAt()),
            false);
        embed.addField("Games", "Pending games: " + status.games().pendingGames()
            + "\nMatch lookups: " + status.games().matchLookups(), false);
        return embed;
    }

    private static EmbedBuilder workerEmbed(QueueWorkerStatus status) {
        EmbedBuilder embed = baseEmbed("Database worker " + status.id() + " (" + status.type() + ")");
        String state = status.state().toUpperCase();
        String current = status.currentJob() == null ? "-" : status.currentJob();
        String position = status.progress() == null
            ? "-"
            : status.progress().current() + "/" + status.progress().total();
        embed.addField("State", state, true);
        embed.addField("Current job", current, false);
        embed.addField("Progress",
            "Position: " + position
                + "\nIn flight: " + status.inFlight()
                + "\nStarted at: " + timestamp(status.currentStartedAt()),
            true);
        embed.setDescription("Queue (" + status.queuedCount() + "):\n" + queue(status.queuedJobs(), status.queuedCount()));
        return embed;
    }

    private static String queue(List<String> jobs, int queuedCount) {
        if (jobs.isEmpty()) return "empty";

        StringBuilder result = new StringBuilder();
        for (int index = 0; index < jobs.size(); index++) {
            String line = (index + 1) + ". " + jobs.get(index) + "\n";
            if (result.length() + line.length() > MAX_QUEUE_DESCRIPTION_LENGTH) {
                result.append("... +").append(queuedCount - index).append(" more");
                return result.toString();
            }
            result.append(line);
        }
        if (jobs.size() < queuedCount) result.append("... +").append(queuedCount - jobs.size()).append(" more");
        return result.toString();
    }

    private static EmbedBuilder baseEmbed(String title) {
        return new EmbedBuilder().setTitle(title).setColor(Bot.getColor());
    }

    private static String state(String value) {
        return value == null ? "IDLE" : value.toUpperCase();
    }

    private static String timestamp(long value) {
        return value <= 0 ? "-" : "<t:" + value / 1000 + ":R>";
    }
}
