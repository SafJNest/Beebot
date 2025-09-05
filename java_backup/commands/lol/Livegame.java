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
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import no.stelar7.api.r4j.pojo.lol.spectator.SpectatorParticipant;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @since 1.3
 */
public class Livegame extends SlashCommand {
    /**
     * Constructor
     */
    public Livegame() {
        this.name = this.getClass().getSimpleName().toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(this.name);
        
        this.aliases = commandData.getAliases();
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        this.arguments = commandData.getArguments();

        this.contexts = new InteractionContextType[]{InteractionContextType.GUILD, InteractionContextType.BOT_DM};

        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "summoner", "Name and tag of the summoner you want to get information on", false).setAutoComplete(true),
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
        
        List<SpectatorParticipant> users = s.getCurrentGame() != null ? s.getCurrentGame().getParticipants() : null;

        StringSelectMenu menu = LeagueMessage.getLivegameMenu(s, users);
        EmbedBuilder builder = LeagueMessage.getLivegameEmbed(s, users);
        List<MessageTopLevelComponent> row = new ArrayList<>(LeagueMessage.getLivegameButtons(s, theGuy != null ? theGuy.getId() : null));

        if (menu != null) {
            row.add(0, ActionRow.of(menu));
            event.getChannel().sendMessageEmbeds(builder.build()).setComponents(row).queue();
        }
        else event.getChannel().sendMessageEmbeds(builder.build()).setComponents(row).queue();
    }

    @Override
	protected void execute(SlashCommandEvent event) {
        event.deferReply(false).queue();
        
        no.stelar7.api.r4j.pojo.lol.summoner.Summoner s = null;

        User theGuy = null;
        if(event.getOption("summoner") == null && event.getOption("user") == null) theGuy = event.getUser();
        else if(event.getOption("user") != null) theGuy = event.getOption("user").getAsUser();
        
        s = LeagueHandler.getSummonerByArgs(event);
        if(s == null){
            event.getHook().editOriginal("Couldn't find the specified summoner. Remember to specify the tag or connect an account using ```/summoner connect```").queue();
            return;
        }

        List<SpectatorParticipant> users = s.getCurrentGame() != null ? s.getCurrentGame().getParticipants() : null;

        StringSelectMenu menu = LeagueMessage.getLivegameMenu(s, users);
        EmbedBuilder builder = LeagueMessage.getLivegameEmbed(s, users);
        List<MessageTopLevelComponent> row = new ArrayList<>(LeagueMessage.getLivegameButtons(s, theGuy != null ? theGuy.getId() : null));

        if (menu != null) {
            row.add(0, ActionRow.of(menu));
            event.getHook().editOriginalEmbeds(builder.build()).setComponents(row).queue();
        }
        else event.getHook().editOriginalEmbeds(builder.build()).setComponents(row).queue();
	}

}
