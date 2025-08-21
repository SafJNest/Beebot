package com.safjnest.commands.guild;

import com.safjnest.sql.BotDB;
import com.safjnest.sql.QueryCollection;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;
import com.safjnest.util.ExperienceSystem;
import com.safjnest.util.SafJNest;
import com.safjnest.util.TableHandler;

import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.util.Arrays;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;

/**
 * @author <a href="https://github.com/NeuntronSun">NeutronSun</a>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 1.3
 */
public class Leaderboard extends SlashCommand {

    public Leaderboard() {
        this.name = this.getClass().getSimpleName().toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(this.name);
        
        this.aliases = commandData.getAliases();
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        this.arguments = commandData.getArguments();
        
        this.options = Arrays.asList(
            new OptionData(OptionType.INTEGER, "limit", "positions limit", false)
        );
        commandData.setThings(this);
    }

    @Override
    protected void execute(CommandEvent event) {
        int limit = (SafJNest.intIsParsable(event.getArgs())) ? Integer.parseInt(event.getArgs()) : 10;

        QueryCollection users = BotDB.getUsersByExp(event.getGuild().getId(), limit);

        if(users.isEmpty()) {
            event.reply("```No Results```");
            return;
        }

        String[][] databaseData = new String[users.size()][users.get(0).size()];
        for(int i = 1; i <= users.size(); i++)
            databaseData[i-1] = users.get(i-1).toArray();
        int rows = databaseData.length;
        int columns = databaseData[0].length + 1;
        String[][] data = new String[rows][columns];
        String[] headers = {"#", "user", "level", "progress", "messages"};
        int lvl, exp;

        for(int i = 0; i < rows; i++) {
            data[i][0] = String.valueOf(i+1);

            data[i][1] = databaseData[i][0];

            lvl = Integer.parseInt(databaseData[i][1]);
            exp = Integer.parseInt(databaseData[i][3]);
            data[i][2] = String.valueOf(lvl);
            data[i][3] = ExperienceSystem.getLvlUpPercentage(lvl, exp) + "% (" + ExperienceSystem.getExpToLvlUp(lvl, exp) + "/" + ExperienceSystem.getExpToReachLvl(lvl) + ") ";
            data[i][4] = databaseData[i][2];
        }

        TableHandler.replaceIdsWithNames(data, event.getJDA());

        String table = TableHandler.constructTable(data, headers);

        String[] splitTable = TableHandler.splitTable(table);

        event.reply(event.getGuild().getName() + " leaderboard:");
        for(int i = 0; i < splitTable.length; i++)
            event.reply("```" + splitTable[i] + "```");
    }

    @Override
	protected void execute(SlashCommandEvent event) {
        int limit = (event.getOption("limit") != null) ? event.getOption("limit").getAsInt() : 10;

        QueryCollection users = BotDB.getUsersByExp(event.getGuild().getId(), limit);
        
        if(users.isEmpty()) {
            event.reply("```No Results```");
            return;
        }

        String[][] databaseData = new String[users.size()][users.get(0).size()];
        for(int i = 1; i <= users.size(); i++)
            databaseData[i-1] = users.get(i-1).toArray();
        int rows = databaseData.length;
        int columns = databaseData[0].length + 1;
        String[][] data = new String[rows][columns];
        String[] headers = {"#", "user", "level", "progress", "messages"};
        int lvl, exp;

        for(int i = 0; i < rows; i++) {
            data[i][0] = String.valueOf(i+1);

            data[i][1] = databaseData[i][0];

            lvl = Integer.parseInt(databaseData[i][1]);
            exp = Integer.parseInt(databaseData[i][3]);
            data[i][2] = String.valueOf(lvl);
            data[i][3] = ExperienceSystem.getLvlUpPercentage(lvl, exp) + "% (" + ExperienceSystem.getExpToLvlUp(lvl, exp) + "/" + ExperienceSystem.getExpToReachLvl(lvl) + ") ";
            data[i][4] = databaseData[i][2];
        }

        TableHandler.replaceIdsWithNames(data, event.getJDA());

        String table = TableHandler.constructTable(data, headers);

        String[] splitTable = TableHandler.splitTable(table);

        event.reply(event.getGuild().getName() + " leaderboard:");

        event.deferReply(false).addContent(event.getGuild().getName() + " leaderboard:").queue();
        for(int i = 0; i < splitTable.length; i++)
            event.getChannel().sendMessage("```" + splitTable[i] + "```").queue();
	}
}