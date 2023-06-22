package com.safjnest.SlashCommands.Misc;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.SlashCommands.ManageGuild.RewardsSlash;
import com.safjnest.SlashCommands.Settings.ToggleExpSystemSlash;
import com.safjnest.Utilities.PermissionHandler;
import com.safjnest.Utilities.Bot.BotSettingsHandler;
import com.safjnest.Utilities.Commands.CommandsLoader;
import com.safjnest.Utilities.Guild.GuildSettings;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

/**
 * This commands once is called sends a message with a full list of all commands, grouped by category.
 * <p>The user can then use the command to get more information about a specific command.</p>
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * 
 * @since 1.1.01
 */
public class HelpSlash extends SlashCommand {
    /**
     * Default constructor for the class.
     */
    GuildSettings gs;
    public HelpSlash(GuildSettings gs) {
        this.name = this.getClass().getSimpleName().replace("Slash", "").toLowerCase();
        this.aliases = new CommandsLoader().getArray(this.name, "alias");
        this.help = new CommandsLoader().getString(this.name, "help");
        this.cooldown = new CommandsLoader().getCooldown(this.name);
        this.category = new Category(new CommandsLoader().getString(this.name, "category"));
        this.arguments = new CommandsLoader().getString(this.name, "arguments");
        this.gs = gs;
        ArrayList<SlashCommand> slashCommandsList = new ArrayList<SlashCommand>();
        Collections.addAll(slashCommandsList, new HelpCommandSlash(gs));
        this.children =  slashCommandsList.toArray(new SlashCommand[slashCommandsList.size()]);
        
    }
    /**
     * This method is called every time a member executes the command.
     */
    @Override
    protected void execute(SlashCommandEvent event) {
        int nCom = 0;
        String command = (event.getOption("command") == null)? "" : event.getOption("command").getAsString();
        EmbedBuilder eb = new EmbedBuilder();
        HashMap<String, ArrayList<SlashCommand>> commands = new HashMap<>();
        for (SlashCommand e : event.getClient().getSlashCommands()) {
            if(!e.isHidden() || PermissionHandler.isUntouchable(event.getMember().getId())){
                if(!commands.containsKey(e.getCategory().getName()))
                    commands.put(e.getCategory().getName(), new ArrayList<SlashCommand>());
                commands.get(e.getCategory().getName()).add(e);
                nCom++;
            }
        }
        eb.setTitle("📒INFO AND COMMAND📒", null);
        eb.setDescription("Current prefix is: **" + gs.getServer(event.getGuild().getId()).getPrefix() + "**\n"
        + "You can get more information using: **/help <command>.**");
        eb.setColor(Color.decode(
            BotSettingsHandler.map.get(event.getJDA().getSelfUser().getId()).color
        ));
            String ss = "```\n";
            for(String k : getKeysInDescendingOrder(commands)){
                Collections.sort(commands.get(k), Comparator.comparing(Command::getName));
                for(Command c : commands.get(k)){
                    ss+= c.getName() + "\n";
                }
                ss+="```";
                eb.addField(k, ss, true);
                ss = "```\n";
            }
            eb.addField("Number of commands avaible:", "```"+nCom+"```", false);
            eb.setFooter("Beebot is continuously updated by the two KINGS ;D", null);
        eb.addField("**OTHER INFORMATION**", "Beebot has been developed by only two people, so dont break the balls", false);
        eb.setAuthor(event.getJDA().getSelfUser().getName(), "https://github.com/SafJNest",
                event.getJDA().getSelfUser().getAvatarUrl());

        event.replyEmbeds(eb.build()).setEphemeral(false).queue();
    }

    public List<String> getKeysInDescendingOrder(HashMap<String, ArrayList<SlashCommand>> map) {
        List<String> keys = new ArrayList<>(map.keySet());
        Collections.sort(keys, new Comparator<String>() {
            @Override
            public int compare(String key1, String key2) {
                return Integer.compare(map.get(key2).size(), map.get(key1).size());
            }
        });
        return keys;
    }

}
