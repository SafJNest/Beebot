package com.safjnest.commands.settings;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.core.Bot;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 1.1
 */
public class AutomatedAction extends SlashCommand {
    
    public AutomatedAction(){
        this.name = this.getClass().getSimpleName().toLowerCase();
        BotCommand commandData = CommandsLoader.getCommand(this.name);
        
        this.aliases = commandData.getAliases();
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        this.arguments = commandData.getArguments();

        this.userPermissions = new Permission[]{Permission.ADMINISTRATOR};

        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "action", "Select the action", true)
                .addChoice("Mute", "1")
                .addChoice("Kick", "2")
                .addChoice("Ban", "3"),
            new OptionData(OptionType.INTEGER, "infractions", "The number of infractions to trigger the action", true)
                .setMinValue(1)
                .setMaxValue(Integer.MAX_VALUE),
            new OptionData(OptionType.ROLE, "action_role", "Role that will be given (only if you select role as action)", false),
            new OptionData(OptionType.STRING, "action_time", "3d1h2m4s. How long your action will last (not use to be permannt)", false),
            new OptionData(OptionType.STRING, "infractions_time", "3d1h2m4s. Period of time to trigger the action", false)
        );

        commandData.setThings(this);
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        int action = event.getOption("action").getAsInt();
        int infractions = event.getOption("infractions").getAsInt();
        
        String actionRole = event.getOption("action_role") != null ? event.getOption("action_role").getAsRole().getId() : null;
        int actionTime = event.getOption("action_time") != null ? convertDurationToSeconds(event.getOption("action_time").getAsString()) : 0;
        int infractions_times = event.getOption("infractions_time") != null ? convertDurationToSeconds(event.getOption("infractions_time").getAsString()) : 0;

        if (action == com.safjnest.model.guild.AutomatedAction.MUTE) {
            actionRole = Bot.getGuildData(event.getGuild()).getMutedRoleId();
            System.out.println(actionRole);
        }

        if (!Bot.getGuildData(event.getGuild()).addAction(action, actionRole, actionTime, infractions, infractions_times)) {
            event.reply("An automated action with the same parameters already exists").queue();
            return;
        }

        event.reply("Automated action added").queue();
    }

    private int convertDurationToSeconds(String duration) {
        int totalSeconds = 0;

        Pattern pattern = Pattern.compile("(\\d+)([dhms])");
        Matcher matcher = pattern.matcher(duration);

        while (matcher.find()) {
            int value = Integer.parseInt(matcher.group(1));
            String unit = matcher.group(2);

            switch (unit) {
                case "d":
                    totalSeconds += value * 86400;
                    break;
                case "h":
                    totalSeconds += value * 3600;
                    break;
                case "m":
                    totalSeconds += value * 60;
                    break;
                case "s":
                    totalSeconds += value;
                    break;
                default:
                    throw new IllegalArgumentException("Invalid duration unit: " + unit);
            }
        }

        return totalSeconds;
    }

}