package com.safjnest.commands.owner;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.core.Chronos.ChronoTask;
import com.safjnest.lol.tracker.Tracker;
import com.safjnest.utils.BotCommand;
import com.safjnest.utils.CommandsLoader;

public class PushQueue extends Command {

    public PushQueue() {
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
        Tracker.QueueStatus before = Tracker.getQueueStatus();
        event.reply("Queue drain started. Profile Statistics: " + before.profileStatistics()
            + ", Champion Data: " + before.championData());

        new ChronoTask() {
            @Override
            public void run() {
                Tracker.QueueDrainResult result = Tracker.processAllQueues();
                String message;
                if (!result.started()) {
                    message = "Queue drain already running. Remaining profile=" + result.profileRemaining()
                        + ", champion=" + result.championRemaining();
                }
                else {
                    message = "Queue drain completed. Processed profile=" + result.profileProcessed()
                        + ", champion=" + result.championProcessed()
                        + ". Remaining profile=" + result.profileRemaining()
                        + ", champion=" + result.championRemaining();
                }
                event.getChannel().sendMessage(message).queue();
            }
        }.queue();
    }
}
