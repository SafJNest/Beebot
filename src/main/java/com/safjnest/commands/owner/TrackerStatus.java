package com.safjnest.commands.owner;

import java.util.List;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.core.Bot;
import com.safjnest.lol.queue.DatabaseTracker;
import com.safjnest.lol.queue.QueueWorkerStatus;
import com.safjnest.lol.tracker.Tracker;
import com.safjnest.lol.tracker.TrackerScheduler;
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
        TrackerScheduler.SchedulerStatus scheduler = TrackerScheduler.status();
        List<QueueWorkerStatus> workers = DatabaseTracker.workerStatuses();

        event.getChannel().sendMessageEmbeds(
            schedulerEmbed(scheduler).build(),
            workerEmbed(workers.get(0)).build(),
            workerEmbed(workers.get(1)).build()
        ).queue();
    }

    private static EmbedBuilder schedulerEmbed(TrackerScheduler.SchedulerStatus status) {
        EmbedBuilder embed = baseEmbed("Tracker status");
        embed.addField("Scheduler", status.scheduled() ? "RUNNING" : "STOPPED", true);
        embed.addField("Tracking", state(status.trackingRunning()), true);
        embed.addField("High elo rank", state(status.highEloRunning()), true);
        embed.addField("Game analysis", state(status.gameQueueRunning()), true);
        embed.addField("Next runs",
            "Tracking: " + timestamp(status.nextTrackingAt())
                + "\nHigh elo rank: " + timestamp(status.nextHighEloAt())
                + "\nGame queue: " + timestamp(status.nextGameQueueAt()),
            false);
        embed.addField("Games", "Pending games: " + Tracker.pendingGameCount()
            + "\nMatch lookups: " + Tracker.pendingMatchLookupCount(), false);
        return embed;
    }

    private static EmbedBuilder workerEmbed(QueueWorkerStatus status) {
        EmbedBuilder embed = baseEmbed("Database worker " + status.id() + " (" + status.type() + ")");
        String state = status.currentJob() == null
            ? (status.running() ? "IDLE" : "STOPPED")
            : "RUNNING";
        String current = status.currentJob() == null ? "-" : status.currentJob();
        String position = status.currentJob() == null
            ? "-"
            : (status.finished() + 1) + "/" + status.submitted();
        embed.addField("State", state, true);
        embed.addField("Current job", current, false);
        embed.addField("Progress",
            "Position: " + position
                + "\nStarted: " + status.started() + "/" + status.submitted()
                + "\nFinished: " + status.finished() + "/" + status.submitted()
                + "\nStarted at: " + timestamp(status.currentStartedAt()),
            true);
        embed.setDescription("Queue (" + status.queuedJobs().size() + "):\n" + queue(status.queuedJobs()));
        return embed;
    }

    private static String queue(List<String> jobs) {
        if (jobs.isEmpty()) return "empty";

        StringBuilder result = new StringBuilder();
        for (int index = 0; index < jobs.size(); index++) {
            String line = (index + 1) + ". " + jobs.get(index) + "\n";
            if (result.length() + line.length() > MAX_QUEUE_DESCRIPTION_LENGTH) {
                result.append("... +").append(jobs.size() - index).append(" more");
                break;
            }
            result.append(line);
        }
        return result.toString();
    }

    private static EmbedBuilder baseEmbed(String title) {
        return new EmbedBuilder().setTitle(title).setColor(Bot.getColor());
    }

    private static String state(boolean running) {
        return running ? "RUNNING" : "IDLE";
    }

    private static String timestamp(long value) {
        return value <= 0 ? "-" : "<t:" + value / 1000 + ":R>";
    }
}
