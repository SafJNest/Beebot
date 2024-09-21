package com.safjnest.commands.lol;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;
import com.safjnest.util.lol.LeagueHandler;
import com.safjnest.util.lol.LeagueMessage;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.LayoutComponent;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @since 1.3
 */
public class Opgg extends SlashCommand {
    /**
     * Constructor
     */
    public Opgg() {
        this.name = this.getClass().getSimpleName().toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(this.name);
        
        this.aliases = commandData.getAliases();
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        this.arguments = commandData.getArguments();

        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "summoner", "Name and tag of the summoner you want to get information on", false),
            LeagueHandler.getLeagueShardOptions(),
            new OptionData(OptionType.USER, "user", "Discord user you want to get information on (if riot account is connected)", false)
        );
        commandData.setThings(this);
    }

    /**
     * This method is called every time a member executes the command.
     */
    @Override
    protected void execute(CommandEvent event) {
        no.stelar7.api.r4j.pojo.lol.summoner.Summoner s = null;

        User theGuy = null;
        if(event.getArgs().equals("")) theGuy = event.getAuthor();    
        else if(event.getMessage().getMentions().getMembers().size() != 0) theGuy = event.getMessage().getMentions().getUsers().get(0);

        s = LeagueHandler.getSummonerByArgs(event);
        if(s == null){
            event.reply("Couldn't find the specified summoner. Remember to use the tag or connect an account.");
            return;
        }
        
        
        EmbedBuilder builder = LeagueMessage.getOpggEmbed(s);
        StringSelectMenu menu = LeagueMessage.getOpggMenu(s);
        List<LayoutComponent> row = new ArrayList<>(LeagueMessage.getOpggButtons(s, theGuy != null ? theGuy.getId() : null));
        row.add(0, LeagueMessage.getOpggQueueTypeButtons(null));
        
        if (menu != null) row.add(0, ActionRow.of(menu));
        else System.out.println("Menu is null");

        event.getChannel().sendMessageEmbeds(builder.build()).setComponents(row).queue();
        
    }

    @Override
	protected void execute(SlashCommandEvent event) {
        no.stelar7.api.r4j.pojo.lol.summoner.Summoner s = null;
        event.deferReply(false).queue();

        s = LeagueHandler.getSummonerByArgs(event);
        if(s == null){
            event.getHook().editOriginal("Couldn't find the specified summoner. Remember to specify the tag or connect an account using ```/summoner connect```").queue();
            return;
        }

        User theGuy = null;
        if(event.getOption("summoner") == null && event.getOption("user") == null) theGuy = event.getUser();
        else if(event.getOption("user") != null) theGuy = event.getOption("user").getAsUser();
        
        EmbedBuilder builder = LeagueMessage.getOpggEmbed(s);
        StringSelectMenu menu = LeagueMessage.getOpggMenu(s);
        List<LayoutComponent> row = new ArrayList<>(LeagueMessage.getOpggButtons(s, theGuy != null ? theGuy.getId() : null));
        row.add(0, LeagueMessage.getOpggQueueTypeButtons(null));
        
        if (menu != null) row.add(0, ActionRow.of(menu));
        else System.out.println("Menu is null");
        
        event.getHook().editOriginalEmbeds(builder.build()).setComponents(row).queue();
	}
    
}
