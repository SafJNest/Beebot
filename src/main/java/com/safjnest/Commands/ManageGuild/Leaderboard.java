package com.safjnest.Commands.ManageGuild;

import com.safjnest.Utilities.CommandsHandler;
import com.safjnest.Utilities.DatabaseHandler;
import com.safjnest.Utilities.Bot.BotSettingsHandler;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;

import java.awt.Color;
import java.util.ArrayList;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;

/**
 * @author <a href="https://github.com/NeuntronSun">NeutronSun</a>
 * 
 * @since 1.3
 */
public class Leaderboard extends Command {

    public Leaderboard() {
        this.name = this.getClass().getSimpleName();
        this.aliases = new CommandsHandler().getArray(this.name, "alias");
        this.help = new CommandsHandler().getString(this.name, "help");
        this.cooldown = new CommandsHandler().getCooldown(this.name);
        this.category = new Category(new CommandsHandler().getString(this.name, "category"));
        this.arguments = new CommandsHandler().getString(this.name, "arguments");
    }

    @Override
    protected void execute(CommandEvent event) {
    
        String query = "SELECT * from exp_table WHERE guild_id = '" + event.getGuild().getId() + "' order by exp DESC;";
        ArrayList<ArrayList<String>> accounts = DatabaseHandler.getSql().getSpecifiedRowBetween(query, 0, 10);
        EmbedBuilder eb = new EmbedBuilder();
        eb.setColor(Color.decode(
                BotSettingsHandler.map.get(event.getJDA().getSelfUser().getId()).color
        ));
        eb.setTitle(":trophy:  "+"**LEADERBOARD**"+" :trophy: ");
        eb.setThumbnail(event.getSelfUser().getAvatarUrl());
        int cont = 1;
        String text = "";
        for(ArrayList<String> user : accounts){
            try {
                User theGuy = event.getJDA().getUserById(user.get(0));
                int lvl = Integer.valueOf(user.get(3));
                String lvlString = String.valueOf(((int) (5 * (Math.pow(lvl, 2)) + (50 * lvl) + 100) - (int) ((5.0/6.0) * (lvl+1) * (2 * (lvl+1) * (lvl+1) + 27 * (lvl+1) + 91) - Integer.valueOf(user.get(2)))) + "/" + (int) (5 * (Math.pow(lvl, 2)) + (50 * lvl) + 100));
                text = "```["+lvl+"] " + theGuy.getName() + ": " + user.get(2) + " exp ("+lvlString+") | " + user.get(4) + " msg.```";
                //[1] Sun: 180000xp (12312/2313) | 312131 msg
                eb.addField("Tier " + String.valueOf(cont), text, false);
            } catch (Exception e) {}
            cont++;
        }
        event.reply(eb.build());
    
    }
}