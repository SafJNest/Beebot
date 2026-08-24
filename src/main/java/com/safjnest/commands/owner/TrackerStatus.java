package com.safjnest.commands.owner;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.core.Bot;
import com.safjnest.lol.model.status.BotStatus;
import com.safjnest.lol.model.status.SchedulerStatus;
import com.safjnest.lol.model.status.QueueStatus;
import com.safjnest.lol.model.status.RunStatus;
import com.safjnest.status.StatusService;
import com.safjnest.utils.BotCommand;
import com.safjnest.utils.CommandsLoader;

import net.dv8tion.jda.api.EmbedBuilder;

public class TrackerStatus extends Command {

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
        EmbedBuilder embed = new EmbedBuilder().setTitle("Request dispatchers").setColor(Bot.getColor());
        for (SchedulerStatus dispatcher : status.dispatchers()) {
            embed.addField(dispatcher.id(), queues(dispatcher) + runs(dispatcher), false);
        }
        event.getChannel().sendMessageEmbeds(embed.build()).queue();
    }

    private static String queues(SchedulerStatus dispatcher) {
        if (dispatcher.queues().isEmpty()) return "No active queues";
        StringBuilder result = new StringBuilder();
        for (QueueStatus queue : dispatcher.queues()) {
            result.append(queue.route())
                .append(": ")
                .append(queue.worker().state())
                .append(" · queued ").append(queue.worker().queuedCount())
                .append(" · in flight ").append(queue.worker().inFlight())
                .append('\n');
        }
        return result.toString();
    }

    private static String runs(SchedulerStatus dispatcher) {
        if (dispatcher.runs().isEmpty()) return "";
        StringBuilder result = new StringBuilder("Runs: ");
        for (RunStatus run : dispatcher.runs()) {
            result.append(run.type()).append(' ')
                .append(run.progress().current()).append('/').append(run.progress().total()).append(" · ");
        }
        return "\n" + result.substring(0, result.length() - 3);
    }
}
