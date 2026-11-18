package com.safjnest.bot.commands.owner;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.bot.audio.SoundboardThumbnails;
import com.safjnest.bot.audio.SoundboardThumbnails.Migration;
import com.safjnest.database.sql.database.BotDB;
import com.safjnest.utils.BotCommand;
import com.safjnest.utils.CommandsLoader;

public final class MigrateSoundboardThumbnails extends Command {

    private static final Logger LOG = LoggerFactory.getLogger(MigrateSoundboardThumbnails.class);
    private static final int REPORTED_FAILURES = 20;
    private static final AtomicBoolean running = new AtomicBoolean();

    public MigrateSoundboardThumbnails() {
        this.name = "migrate-soundboard-thumbnails";

        BotCommand command = CommandsLoader.getCommand(name);
        this.help = command.getHelp();
        this.arguments = command.getArguments();
        this.category = command.getCategory();
        this.ownerCommand = true;
        this.hidden = true;

        command.setThings(this);
    }

    @Override
    protected void execute(CommandEvent event) {
        String args = event.getArgs().strip();
        if (args.equals("--dry-run")) {
            Thread.startVirtualThread(() -> countPending(event));
            return;
        }
        if (!args.isEmpty()) {
            event.reply("Usage: " + name + " [--dry-run]");
            return;
        }
        if (!running.compareAndSet(false, true)) {
            event.reply("A soundboard thumbnail migration is already running.");
            return;
        }

        event.reply("Migrating soundboard thumbnails to R2.");
        Thread.startVirtualThread(() -> migrate(event));
    }

    /* -------------------------------------------------------------------------- */

    private void countPending(CommandEvent event) {
        try {
            int pending = BotDB.get().countPendingSoundboardThumbnails();
            event.reply(pending + " soundboard thumbnail(s) pending.");
        } catch (RuntimeException exception) {
            LOG.warn("Could not count pending soundboard thumbnails", exception);
            event.reply("Could not count pending thumbnails. Check the bot logs.");
        }
    }

    private void migrate(CommandEvent event) {
        try {
            Migration result = SoundboardThumbnails.configured().migrate();
            List<Integer> failures = result.failedIds();
            String message = "Migrated %d, skipped %d, failed %d, pending %d.".formatted(
                result.migrated(), result.skipped(), failures.size(), result.pending());

            if (!failures.isEmpty()) {
                List<Integer> reported = failures.subList(0, Math.min(failures.size(), REPORTED_FAILURES));
                message += " Failed soundboard IDs: " + reported;
                if (failures.size() > REPORTED_FAILURES)
                    message += " (see bot logs for all failures)";
            }

            event.reply(message);
        } catch (RuntimeException exception) {
            LOG.warn("Soundboard thumbnail migration stopped", exception);
            event.reply("Thumbnail migration stopped. Check the bot logs and run it again to resume.");
        } finally {
            running.set(false);
        }
    }
}
