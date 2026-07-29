package com.safjnest.commands.owner;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.lol.service.ChampionDataRefreshService.MatrixRefreshResult;
import com.safjnest.lol.tracker.DatabaseTracker;
import com.safjnest.utils.BotCommand;
import com.safjnest.utils.CommandsLoader;

import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;

public class ChampionStats extends Command {

    public ChampionStats() {
        this.name = this.getClass().getSimpleName().toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(this.name);
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
        String rawArguments = event.getArgs() == null ? "" : event.getArgs().trim();
        String[] arguments = rawArguments.isBlank() ? new String[0] : rawArguments.split("\\s+");
        if (arguments.length < 2) {
            event.reply("Usage: championstats <patch> <queue>");
            return;
        }

        String patch = arguments[0];
        if (!patch.matches("\\d+\\.\\d+")) {
            event.reply("Invalid patch. Expected major.minor, for example 15.14.");
            return;
        }

        GameQueueType queue;
        try {
            queue = GameQueueType.valueOf(arguments[1].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            event.reply("Invalid queue: " + arguments[1]);
            return;
        }

        CompletableFuture<MatrixRefreshResult> future = DatabaseTracker.enqueueChampionStatsMatrix(patch, queue);
        event.reply("Champion stats matrix queued for patch=" + patch + ", queue=" + queue.name());
        future.whenComplete((result, error) -> {
            if (error != null) {
                event.getChannel().sendMessage("Champion stats matrix failed: " + error.getMessage()).queue();
                return;
            }
            event.getChannel().sendMessage("Champion stats matrix completed: combinations="
                + result.combinations() + ", skipped=" + result.skipped()
                + ", generated=" + result.generated() + ", empty=" + result.empty()
                + ", champions=" + result.persistedChampions()).queue();
        });
    }
}
